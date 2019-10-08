package io.coti.basenode.communication;

import io.coti.basenode.communication.interfaces.ISubscriberMessageType;
import io.coti.basenode.data.*;
import io.coti.basenode.services.interfaces.*;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public enum SubscriberMessageType implements ISubscriberMessageType {
    TransactionData {
        @Override
        public Consumer<Object> getHandler(NodeType publisherNodeType) {
            return transactionData -> transactionService.handlePropagatedTransaction((TransactionData) transactionData);
        }
    },
    AddressData {
        @Override
        public Consumer<Object> getHandler(NodeType publisherNodeType) {
            return addressData -> addressService.handlePropagatedAddress((AddressData) addressData);
        }
    },
    DspConsensusResult {
        @Override
        public Consumer<Object> getHandler(NodeType publisherNodeType) {
            return dspConsensusResult -> dspVoteService.handleVoteConclusion((DspConsensusResult) dspConsensusResult);
        }
    },
    NetworkData {
        @Override
        public Consumer<Object> getHandler(NodeType publisherNodeType) {
            return networkData -> networkService.handleNetworkChanges((NetworkData) networkData);
        }
    },
    CurrencyNoticeData {
        @Override
        public Consumer<Object> getHandler(NodeType publisherNodeType) {
            return currencyNoticeData -> currencyService.handlePropagatedCurrencyNotice((CurrencyNoticeData) currencyNoticeData);
        }
    }
    ;

    public ITransactionService transactionService;
    public IAddressService addressService;
    public IDspVoteService dspVoteService;
    public INetworkService networkService;
    public ICurrencyService currencyService;

}
