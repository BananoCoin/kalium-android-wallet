package com.banano.kaliumwallet.network.model.response;

import com.banano.kaliumwallet.network.model.BaseResponse;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class AccountBalanceItem extends BaseResponse implements Serializable {
    @SerializedName("balance")
    private String balance;

    @SerializedName("pending")
    private String pending;

    private String privKey;

    private String frontier;

    private PendingTransactionResponse pendingTransactions;

    public AccountBalanceItem() {
    }

    public AccountBalanceItem(String privKey) {
        this.privKey = privKey;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public String getPending() {
        return pending;
    }

    public void setPending(String pending) {
        this.pending = pending;
    }

    public String getPrivKey() {
        return privKey;
    }

    public void setPrivKey(String privKey) {
        this.privKey = privKey;
    }

    public String getFrontier() {
        return frontier;
    }

    public void setFrontier(String frontier) {
        this.frontier = frontier;
    }

    public PendingTransactionResponse getPendingTransactions() {
        return pendingTransactions;
    }

    public void setPendingTransactions(PendingTransactionResponse pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
    }
}
