package io.coti.zerospend.controllers;

import io.coti.basenode.data.Event;
import io.coti.basenode.http.interfaces.IResponse;
import io.coti.zerospend.http.SetIndexesRequest;
import io.coti.zerospend.services.TransactionCreationService;
import io.coti.zerospend.services.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private TransactionCreationService transactionCreationService;

    @PutMapping(path = "/transaction/index")
    public ResponseEntity<IResponse> setIndexToTransactions(@RequestBody SetIndexesRequest setIndexesRequest) {
        return transactionService.setIndexToTransactions(setIndexesRequest);
    }

    @PostMapping(path = "/event/multi-dag")
    public ResponseEntity<IResponse> eventMultiDag() {
        return transactionCreationService.createEventTransaction("Multi DAG", Event.MULTI_DAG
        );
    }

    @PostMapping(path = "/event/trust-score-consensus")
    public ResponseEntity<IResponse> eventTrustScoreConsensus() {
        return transactionCreationService.createEventTransaction("Trust Score Consensus", Event.TRUST_SCORE_CONSENSUS
        );
    }

}
