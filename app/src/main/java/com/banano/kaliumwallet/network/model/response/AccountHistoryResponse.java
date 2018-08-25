package com.banano.kaliumwallet.network.model.response;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import com.banano.kaliumwallet.network.model.BaseResponse;

/**
 * Account history
 */

public class AccountHistoryResponse extends BaseResponse {
    // entries are sorted newest to oldest
    @SerializedName("history")
    private List<AccountHistoryResponseItem> history;

    public AccountHistoryResponse() {
    }

    public AccountHistoryResponse(List<AccountHistoryResponseItem> history) {
        this.history = history;
    }

    public List<AccountHistoryResponseItem> getHistory() {
        return history;
    }

    public void setHistory(List<AccountHistoryResponseItem> history) {
        this.history = history;
    }
}
