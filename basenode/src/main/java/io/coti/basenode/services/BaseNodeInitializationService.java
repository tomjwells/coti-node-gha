package io.coti.basenode.services;

import io.coti.basenode.communication.JacksonSerializer;
import io.coti.basenode.communication.interfaces.IPropagationSubscriber;
import io.coti.basenode.crypto.GetNodeRegistrationRequestCrypto;
import io.coti.basenode.crypto.NetworkNodeCrypto;
import io.coti.basenode.crypto.NodeRegistrationCrypto;
import io.coti.basenode.data.*;
import io.coti.basenode.database.Interfaces.IDatabaseConnector;
import io.coti.basenode.http.CustomHttpComponentsClientHttpRequestFactory;
import io.coti.basenode.http.GetNodeRegistrationRequest;
import io.coti.basenode.http.GetNodeRegistrationResponse;
import io.coti.basenode.model.AddressTransactionsHistories;
import io.coti.basenode.model.NodeRegistrations;
import io.coti.basenode.model.Transactions;
import io.coti.basenode.services.LiveView.LiveViewService;
import io.coti.basenode.services.interfaces.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public abstract class BaseNodeInitializationService {

    private final static String NODE_REGISTRATION = "/node/node_registration";
    private final static String NODE_MANAGER_NODES_ENDPOINT = "/nodes";
    private final static String RECOVERY_NODE_GET_BATCH_ENDPOINT = "/transaction_batch";
    private final static String STARTING_INDEX_URL_PARAM_ENDPOINT = "?starting_index=";
    private final static long MAXIMUM_BUFFER_SIZE = 10000;
    @Autowired
    protected INetworkService networkService;
    @Value("${network}")
    protected NetworkType networkType;
    @Value("${server.ip}")
    protected String nodeIp;
    @Value("${node.manager.ip}")
    private String nodeManagerIp;
    @Value("${node.manager.port}")
    private String nodeManagerPort;
    @Value("${node.manager.propagation.port}")
    private String nodeManagerPropagationPort;
    private String nodeManagerHttpAddress;
    @Value("${kycserver.url}")
    private String kycServerAddress;
    @Value("${kycserver.public.key}")
    private String kycServerPublicKey;
    @Autowired
    private Transactions transactions;
    @Autowired
    private AddressTransactionsHistories addressTransactionsHistories;
    @Autowired
    private TransactionIndexService transactionIndexService;
    @Autowired
    private IBalanceService balanceService;
    @Autowired
    private IConfirmationService confirmationService;
    @Autowired
    private IClusterService clusterService;
    @Autowired
    private IMonitorService monitorService;
    @Autowired
    private LiveViewService liveViewService;
    @Autowired
    private ITransactionHelper transactionHelper;
    @Autowired
    private ITransactionService transactionService;
    @Autowired
    private IAddressService addressService;
    @Autowired
    private IDspVoteService dspVoteService;
    @Autowired
    private IPotService potService;
    @Autowired
    private IDatabaseConnector databaseConnector;
    @Autowired
    private IPropagationSubscriber propagationSubscriber;
    @Autowired
    private IShutDownService shutDownService;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private GetNodeRegistrationRequestCrypto getNodeRegistrationRequestCrypto;
    @Autowired
    private NodeRegistrationCrypto nodeRegistrationCrypto;
    @Autowired
    private NetworkNodeCrypto networkNodeCrypto;
    @Autowired
    private NodeRegistrations nodeRegistrations;
    @Autowired
    private IClusterStampService clusterStampService;
    @Autowired
    private JacksonSerializer jacksonSerializer;

    public void init() {
        try {
            addressService.init();
            balanceService.init();
            clusterStampService.loadClusterStamp();
            confirmationService.init();
            dspVoteService.init();
            transactionService.init();
            potService.init();
            initCommunication();
            log.info("The communication initialization is done");
            initTransactionSync();
            log.info("The transaction sync initialization is done");
            networkService.setConnectToNetworkUrl(nodeManagerHttpAddress + NODE_MANAGER_NODES_ENDPOINT);
            networkService.connectToNetwork();
            propagationSubscriber.initPropagationHandler();

            monitorService.init();
        } catch (Exception e) {
            log.error("Errors at {} : ", this.getClass().getSimpleName(), e);
            System.exit(-1);
        }
    }

    private void initTransactionSync() {
        try {
            AtomicLong maxTransactionIndex = new AtomicLong(-1);
            log.info("Starting to read existing transactions");
            AtomicLong completedExistedTransactionNumber = new AtomicLong(0);
            List<Callable<Object>> existingTransactionTasks = new ArrayList<>();
            transactions.forEach(transactionData ->
                    existingTransactionTasks.add(Executors.callable(() -> {
                                handleExistingTransaction(maxTransactionIndex, transactionData);
                                completedExistedTransactionNumber.incrementAndGet();
                            }
                    )));
            if (existingTransactionTasks.size() != 0) {
                ExecutorService existingTransactionExecutorService = Executors.newSingleThreadExecutor();
                Thread monitorExistingTransactions = monitorTransactionThread("existing", completedExistedTransactionNumber, null);
                monitorExistingTransactions.start();
                existingTransactionExecutorService.invokeAll(existingTransactionTasks);
                monitorExistingTransactions.interrupt();
                monitorExistingTransactions.join();
            }
            transactionIndexService.init(maxTransactionIndex);
            log.info("Finished to read existing transactions");

            if (networkService.getRecoveryServerAddress() != null) {
                requestMissingTransactions(transactionIndexService.getLastTransactionIndexData().getIndex() + 1);
            }
            balanceService.validateBalances();
            log.info("Transactions Load completed");
            clusterService.finalizeInit();

        } catch (Exception e) {
            log.error("Fatal error in initialization", e);
            System.exit(-1);
        }
    }

    private void initCommunication() {
        networkService.setNodeManagerPropagationAddress("tcp://" + nodeManagerIp + ":" + nodeManagerPropagationPort);

        propagationSubscriber.connectAndSubscribeToServer(networkService.getNodeManagerPropagationAddress(), NodeType.NodeManager);
        propagationSubscriber.startListening();
    }

    public void initDB() {
        databaseConnector.init();
    }

    private void handleExistingTransaction(AtomicLong maxTransactionIndex, TransactionData transactionData) {
        if (!transactionData.isTrustChainConsensus()) {
            clusterService.addUnconfirmedTransaction(transactionData);
        }
        liveViewService.addTransaction(transactionData);
        confirmationService.insertSavedTransaction(transactionData);
        if (transactionData.getDspConsensusResult() != null) {
            maxTransactionIndex.set(Math.max(maxTransactionIndex.get(), transactionData.getDspConsensusResult().getIndex()));
        } else {
            transactionHelper.addNoneIndexedTransaction(transactionData);
        }
        transactionService.addToExplorerIndexes(transactionData);
        transactionHelper.incrementTotalTransactions();
    }

    private void handleMissingTransaction(TransactionData transactionData) {
        if (transactionHelper.isTransactionAlreadyPropagated(transactionData)) {
            log.debug("Transaction already exists: {}", transactionData.getHash());
            return;
        }
        transactions.put(transactionData);
        if (!transactionData.isTrustChainConsensus()) {
            clusterService.addUnconfirmedTransaction(transactionData);
        }

        liveViewService.addTransaction(transactionData);
        transactionService.addToExplorerIndexes(transactionData);
        transactionHelper.incrementTotalTransactions();

        confirmationService.insertMissingTransaction(transactionData);
        propagateMissingTransaction(transactionData);
    }

    private void requestMissingTransactions(long firstMissingTransactionIndex) {
        try {
            log.info("Starting to get missing transactions");
            List<TransactionData> missingTransactions = new ArrayList<>();
            AtomicLong completedMissingTransactionNumber = new AtomicLong(0);
            AtomicLong receivedMissingTransactionNumber = new AtomicLong(0);
            Thread monitorMissingTransactionThread = monitorTransactionThread("missing", completedMissingTransactionNumber, receivedMissingTransactionNumber);
            Thread insertMissingTransactionThread = insertMissingTransactionThread(missingTransactions, completedMissingTransactionNumber, monitorMissingTransactionThread);
            ResponseExtractor responseExtractor = response -> {
                byte[] buf = new byte[Math.toIntExact(MAXIMUM_BUFFER_SIZE)];
                int offset = 0;
                int n;
                while ((n = response.getBody().read(buf, offset, buf.length)) > 0) {
                    try {
                        TransactionData missingTransaction = jacksonSerializer.deserialize(buf);
                        if (missingTransaction != null) {
                            missingTransactions.add(missingTransaction);
                            receivedMissingTransactionNumber.incrementAndGet();
                            if (!insertMissingTransactionThread.isAlive()) {
                                insertMissingTransactionThread.start();
                            }
                            Arrays.fill(buf, 0, offset + n, (byte) 0);
                            offset = 0;
                        } else {
                            offset += n;
                        }

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                return null;
            };
            restTemplate.execute(networkService.getRecoveryServerAddress() + "/transaction_batch"
                    + STARTING_INDEX_URL_PARAM_ENDPOINT + firstMissingTransactionIndex, HttpMethod.GET, null, responseExtractor);
            if (insertMissingTransactionThread.isAlive()) {
                log.info("Received all {} missing transactions from recovery server", receivedMissingTransactionNumber);
                insertMissingTransactionThread.interrupt();
                insertMissingTransactionThread.join();
            }
            log.info("Finished to get missing transactions");
        } catch (Exception e) {
            log.error("Error at missing transactions from recovery Node");
            throw new RuntimeException(e);
        }

    }

    private Thread insertMissingTransactionThread(List<TransactionData> missingTransactions, AtomicLong completedMissingTransactionNumber, Thread monitorMissingTransactionThread) throws Exception {
        return new Thread(() -> {
            Map<Hash, AddressTransactionsHistory> addressToTransactionsHistoryMap = new ConcurrentHashMap<>();
            int offset = 0;
            int nextOffSet;
            int missingTransactionsSize;
            monitorMissingTransactionThread.start();
            while ((missingTransactionsSize = missingTransactions.size()) > offset || !Thread.currentThread().isInterrupted()) {

                if (missingTransactionsSize - 1 > offset || (missingTransactionsSize - 1 == offset && missingTransactions.get(offset) != null)) {
                    nextOffSet = offset + (Thread.currentThread().isInterrupted() ? missingTransactionsSize - offset : 1);
                    for (int i = offset; i < nextOffSet; i++) {
                        TransactionData transactionData = missingTransactions.get(i);
                        handleMissingTransaction(transactionData);
                        transactionHelper.updateAddressTransactionHistory(addressToTransactionsHistoryMap, transactionData);
                        completedMissingTransactionNumber.incrementAndGet();
                    }
                    offset = nextOffSet;
                }
            }
            addressTransactionsHistories.putBatch(addressToTransactionsHistoryMap);
            monitorMissingTransactionThread.interrupt();
            try {
                monitorMissingTransactionThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

    }

    private Thread monitorTransactionThread(String type, AtomicLong transactionNumber, AtomicLong receivedTransactionNumber) {
        return new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (receivedTransactionNumber != null) {
                    log.info("Received {} transactions: {}, inserted transactions: {}", type, receivedTransactionNumber, transactionNumber);
                } else {
                    log.info("Inserted {} transactions: {}", type, transactionNumber);
                }
            }
        });
    }

    protected void createNetworkNodeData() {
        networkService.init();
        NetworkNodeData networkNodeData = createNodeProperties();
        NodeRegistrationData nodeRegistrationData = nodeRegistrations.getByHash(networkNodeData.getHash());
        if (nodeRegistrationData != null) {
            networkNodeData.setNodeRegistrationData(nodeRegistrationData);
        } else {
            getNodeRegistration(networkNodeData);
        }
        networkNodeCrypto.signMessage(networkNodeData);
        networkService.setNetworkNodeData(networkNodeData);
    }

    protected void getNetwork() {

        nodeManagerHttpAddress = "http://" + nodeManagerIp + ":" + nodeManagerPort;
        networkService.setNetworkData(getNetworkDetailsFromNodeManager());
    }

    private NetworkData getNetworkDetailsFromNodeManager() {
        return restTemplate.getForEntity(nodeManagerHttpAddress + NODE_MANAGER_NODES_ENDPOINT, NetworkData.class).getBody();
    }

    private void getNodeRegistration(NetworkNodeData networkNodeData) {
        try {
            restTemplate.setRequestFactory(new CustomHttpComponentsClientHttpRequestFactory());
            GetNodeRegistrationRequest getNodeRegistrationRequest = new GetNodeRegistrationRequest(networkNodeData.getNodeType(), networkType);
            getNodeRegistrationRequestCrypto.signMessage(getNodeRegistrationRequest);

            ResponseEntity<GetNodeRegistrationResponse> getNodeRegistrationResponseEntity =
                    restTemplate.postForEntity(
                            kycServerAddress + NODE_REGISTRATION,
                            getNodeRegistrationRequest,
                            GetNodeRegistrationResponse.class);
            log.info("Node registration received");

            NodeRegistrationData nodeRegistrationData = getNodeRegistrationResponseEntity.getBody().getNodeRegistrationData();
            if (nodeRegistrationData != null && validateNodeRegistrationResponse(nodeRegistrationData, networkNodeData)) {
                networkNodeData.setNodeRegistrationData(nodeRegistrationData);
                nodeRegistrations.put(nodeRegistrationData);
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Error at registration of node. Registrar response: \n {}", e.getResponseBodyAsString());
            System.exit(-1);
        }
    }

    protected boolean validateNodeRegistrationResponse(NodeRegistrationData nodeRegistrationData, NetworkNodeData networkNodeData) {
        if (!nodeRegistrationData.getSignerHash().toString().equals(kycServerPublicKey)) {
            log.error("Invalid kyc server public key.");
            System.exit(-1);
        }
        if (!networkNodeData.getNodeHash().equals(nodeRegistrationData.getNodeHash()) || !networkNodeData.getNodeType().equals(nodeRegistrationData.getNodeType())) {
            log.error("Node registration response has invalid fields! Shutting down server.");
            System.exit(-1);

        }

        if (!nodeRegistrationCrypto.verifySignature(nodeRegistrationData)) {
            log.error("Node registration failed signature validation! Shutting down server");
            System.exit(-1);
        }

        return true;
    }

    protected abstract NetworkNodeData createNodeProperties();

    protected void propagateMissingTransaction(TransactionData transactionData) {

    }

    @PreDestroy
    public void shutdown() {
        shutDownService.shutdown();
    }

}
