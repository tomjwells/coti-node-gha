package io.coti.historynode.crypto;

import io.coti.basenode.crypto.CryptoHelper;
import io.coti.basenode.crypto.SignatureValidationCrypto;
import io.coti.historynode.http.GetTransactionsByAddressRequest;
import org.springframework.stereotype.Component;

@Component
public class GetTransactionsByAddressRequestCrypto extends SignatureValidationCrypto<GetTransactionsByAddressRequest> {

    //TODO 6/30/2019 tomer: remove this and leave only TransactionsRequestCrypto for the generic request
    @Override
    public byte[] getSignatureMessage(GetTransactionsByAddressRequest getTransactionsByAddressRequest) {
        byte[] addressInBytes = getTransactionsByAddressRequest.getAddress().getBytes();
        return CryptoHelper.cryptoHash(addressInBytes).getBytes();
    }
}
