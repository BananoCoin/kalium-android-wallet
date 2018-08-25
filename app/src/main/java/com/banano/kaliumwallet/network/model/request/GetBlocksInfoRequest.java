package com.banano.kaliumwallet.network.model.request;

import com.google.gson.annotations.SerializedName;

import com.banano.kaliumwallet.network.model.Actions;
import com.banano.kaliumwallet.network.model.BaseRequest;

/**
 * Retrieve hash history
 */

public class GetBlocksInfoRequest extends BaseRequest {
    @SerializedName("action")
    private String action;

    @SerializedName("hashes")
    private String[] hashes;

    @SerializedName("balance")
    private boolean balance;

    public GetBlocksInfoRequest() {
        this.action = Actions.GET_BLOCKS_INFO.toString();
        this.hashes = new String[] {""};
        this.balance = true;
    }

    public GetBlocksInfoRequest(String[] hashes) {
        this.action = Actions.GET_BLOCKS_INFO.toString();
        this.hashes = hashes;
        this.balance = true;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String[] getHashes() {
        return hashes;
    }

    public void setHashes(String[] hashes) {
        this.hashes = hashes;
    }
}
