package com.banano.kaliumwallet.bus;

import com.banano.kaliumwallet.network.model.response.AccountHistoryResponse;

public class TransferHistoryResponse {
    private AccountHistoryResponse accountHistoryResponse;
    private String account;

    public TransferHistoryResponse(AccountHistoryResponse accountHistoryResponse, String account) {
        this.accountHistoryResponse = accountHistoryResponse;
        this.account = account;
    }

    public AccountHistoryResponse getAccountHistoryResponse() {
        return accountHistoryResponse;
    }

    public void setAccountHistoryResponse(AccountHistoryResponse accountHistoryResponse) {
        this.accountHistoryResponse = accountHistoryResponse;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }
}
