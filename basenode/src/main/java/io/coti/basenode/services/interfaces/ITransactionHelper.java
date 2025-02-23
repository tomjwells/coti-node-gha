package io.coti.basenode.services.interfaces;

import io.coti.basenode.data.*;
import io.coti.basenode.data.interfaces.ITrustScoreNodeValidatable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ITransactionHelper {

    boolean validateBaseTransactionAmounts(List<BaseTransactionData> baseTransactions);

    boolean validateBaseTransactionsDataIntegrity(TransactionData transactionData);

    boolean validateTransactionTimeFields(TransactionData transactionData);

    void updateAddressTransactionHistory(TransactionData transactionData);

    void updateAddressTransactionHistory(Map<Hash, AddressTransactionsHistory> addressToTransactionsHistoryMap, TransactionData transactionData);

    boolean validateTransactionCrypto(TransactionData transactionData);

    boolean validateTransactionType(TransactionData transactionData);

    boolean validateTrustScore(TransactionData transactionData);

    void startHandleTransaction(TransactionData transactionData);

    void endHandleTransaction(TransactionData transactionData);

    boolean isTransactionFinished(TransactionData transactionData);

    boolean checkBalancesAndAddToPreBalance(TransactionData transactionData);

    boolean validateCurrencyUniquenessAndAddUnconfirmedRecord(TransactionData transactionData);

    void attachTransactionToCluster(TransactionData transactionData);

    void setTransactionStateToSaved(TransactionData transactionData);

    void setTransactionStateToFinished(TransactionData transactionData);

    void updateTransactionOnCluster(TransactionData transactionData);

    boolean isConfirmed(TransactionData transactionData);

    boolean isDspConfirmed(TransactionData transactionData);

    Hash getReceiverBaseTransactionAddressHash(TransactionData transactionData);

    Hash getReceiverBaseTransactionHash(TransactionData transactionData);

    BigDecimal getRollingReserveAmount(TransactionData transactionData);

    PaymentInputBaseTransactionData getPaymentInputBaseTransaction(TransactionData transactionData);

    boolean isTransactionExists(TransactionData transactionData);

    boolean isTransactionHashExists(Hash transactionHash);

    boolean validateBaseTransactionTrustScoreNodeResults(TransactionData transactionData);

    boolean validateBaseTransactionTrustScoreNodeResult(ITrustScoreNodeValidatable baseTransactionData);

    boolean isTransactionHashProcessing(Hash transactionHash);

    boolean isTransactionAlreadyPropagated(TransactionData transactionData);

    TokenGenerationFeeBaseTransactionData getTokenGenerationFeeData(TransactionData tokenGenerationTransaction);

    TokenMintingFeeBaseTransactionData getTokenMintingFeeData(TransactionData tokenMintingTransaction);

    long getTotalTransactions();

    long incrementTotalTransactions();

    void addNoneIndexedTransaction(TransactionData transactionData);

    void removeNoneIndexedTransaction(TransactionData transactionData);

    Set<Hash> getNoneIndexedTransactionHashes();

    boolean checkTokenMintingAndAddToAllocatedAmount(TransactionData transactionData);

    boolean checkEventHardForkAndAddToEvents(TransactionData transactionData);

    EventInputBaseTransactionData getEventInputBaseTransactionData(TransactionData eventTransactionData);

    BigDecimal getNativeAmount(TransactionData transactionData);

    TransactionData createNewTransaction(List<BaseTransactionData> baseTransactions, Hash transactionHash, String transactionDescription, List<TransactionTrustScoreData> trustScoreResults, Instant createTime, Hash senderHash, SignatureData senderSignature, TransactionType type);

    TransactionData createNewTransaction(List<BaseTransactionData> baseTransactions, String transactionDescription, double senderTrustScore, Instant createTime, TransactionType type);

    boolean validateBaseTransactionPublicKey(BaseTransactionData baseTransactionData, NodeType nodeType);

}
