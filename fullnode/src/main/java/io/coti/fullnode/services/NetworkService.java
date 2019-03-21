package io.coti.fullnode.services;

import io.coti.basenode.communication.interfaces.ISender;
import io.coti.basenode.data.*;
import io.coti.basenode.data.interfaces.IPropagatable;
import io.coti.basenode.services.BaseNodeNetworkService;
import io.coti.basenode.services.interfaces.ICommunicationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class NetworkService extends BaseNodeNetworkService {

    @Autowired
    private ICommunicationService communicationService;
    @Autowired
    private ISender sender;
    private List<NetworkNodeData> connectedDspNodes = new ArrayList<>(2);

    @Override
    public void handleNetworkChanges(NetworkData newNetworkData) {
        log.info("New network structure received");
        Map<Hash, NetworkNodeData> newDspNodeMap = newNetworkData.getMultipleNodeMaps().get(NodeType.DspNode);

        handleConnectedDspNodesChange(connectedDspNodes, newDspNodeMap, NodeType.FullNode);

        if (connectedDspNodes.size() < 2) {
            List<NetworkNodeData> dspNodesToConnect = new ArrayList<>(CollectionUtils.subtract(newDspNodeMap.values(), connectedDspNodes));
            Collections.shuffle(dspNodesToConnect);
            for (int i = 0; i < dspNodesToConnect.size() && i < 2 - connectedDspNodes.size(); i++) {
                if (i == 0 && recoveryServerAddress == null) {
                    recoveryServerAddress = dspNodesToConnect.get(0).getHttpFullAddress();
                }
                communicationService.addSubscription(dspNodesToConnect.get(i).getPropagationFullAddress(), NodeType.DspNode);
                communicationService.addSender(dspNodesToConnect.get(i).getReceivingFullAddress());
            }
        }

        setNetworkData(newNetworkData);

    }

    public void addToConnectedDspNodes(NetworkNodeData networkNodeData) {
        connectedDspNodes.add(networkNodeData);
    }

    public void sendDataToConnectedDspNodes(IPropagatable propagatable) {
        connectedDspNodes.forEach(networkNodeData -> sender.send(propagatable, networkNodeData.getReceivingFullAddress()));
    }

    public void sendDataToConnectedDspsByHttp(MessageArrivalValidationData data){
        RestTemplate restTemplate = new RestTemplate();
        connectedDspNodes.forEach(networkNodeData -> sendMessageArrivalValidationDataToConnectedDsp(restTemplate, buildMessageArrivalValidationHttpUrl(networkNodeData), data));

    }

    private String buildMessageArrivalValidationHttpUrl(NetworkNodeData networkNodeData){
        StringBuilder sb = new StringBuilder();
        sb.append("http://").
                append(networkNodeData.getAddress()).
                append(":").
                append(networkNodeData.getHttpPort()).append("/missedDataHashes");
        return sb.toString();
    }

    private MessageArrivalValidationData sendMessageArrivalValidationDataToConnectedDsp(RestTemplate restTemplate, String uri, MessageArrivalValidationData data){
        log.info("Sending MessageArrivalValidationData to DSP: {}", data);
        try {
            ResponseEntity<MessageArrivalValidationData> response =
                    restTemplate.postForEntity(
                            uri,
                            data,
                            MessageArrivalValidationData.class);

            log.info("Received missedDataHashes response: {}", response.getBody());
            response.getBody().getClassNameToHashes().keySet().forEach(k -> log.info("Missed {} messages: {}", k, ((Set<? extends DataHash>)response.getBody().getClassNameToHashes().get(k)).toString()));

            return response.getBody();
        }catch (Exception e){
            log.info(e.getMessage());
        }
        return null;
    }


}
