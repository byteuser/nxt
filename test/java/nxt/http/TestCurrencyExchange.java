package nxt.http;

import nxt.AccountCurrencyBalance;
import nxt.BlockchainTest;
import nxt.Constants;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class TestCurrencyExchange extends BlockchainTest {

    @Test
    public void buyCurrency() {
        String currencyId = TestCurrencyIssuance.issueCurrencyImpl();
        AccountCurrencyBalance initialSellerBalance = new AccountCurrencyBalance(secretPhrase1, currencyId);
        AccountCurrencyBalance initialBuyerBalance = new AccountCurrencyBalance(secretPhrase2, currencyId);

        Assert.assertEquals(100000, initialSellerBalance.getCurrencyUnits());
        Assert.assertEquals(100000, initialSellerBalance.getUnconfirmedCurrencyUnits());

        JSONObject publishExchangeOfferResponse = publishExchangeOffer(currencyId);

        generateBlock();

        APICall apiCall = new APICall.Builder("getAllOffers").build();
        JSONObject getAllOffersResponse = apiCall.invoke();
        Logger.logDebugMessage("getAllOffersResponse:" + getAllOffersResponse.toJSONString());
        JSONArray offer = (JSONArray)getAllOffersResponse.get("openOffers");
        Assert.assertEquals(publishExchangeOfferResponse.get("transaction"), ((JSONObject)offer.get(0)).get("offer"));

        // The buy offer reduces the unconfirmed balance but does not change the confirmed balance
        // The sell offer reduces the unconfirmed currency units and confirmed units
        AccountCurrencyBalance afterOfferSellerBalance = new AccountCurrencyBalance(secretPhrase1, currencyId);
        Assert.assertEquals(new AccountCurrencyBalance(-1000*95 - Constants.ONE_NXT, -Constants.ONE_NXT, -500, 0),
                afterOfferSellerBalance.diff(initialSellerBalance));

        // buy at rate higher than sell offer results in selling at sell offer
        apiCall = new APICall.Builder("currencyBuy").
                secretPhrase(secretPhrase2).feeNQT(Constants.ONE_NXT).
                param("currency", currencyId).
                param("rateNQT", "" + 106).
                param("units", "200").
                build();
        JSONObject currencyExchangeResponse = apiCall.invoke();
        Logger.logDebugMessage("currencyExchangeResponse:" + currencyExchangeResponse);
        generateBlock();

        // This is tricky, the buyer committed to invest 106*200=21200 NXT
        // However the price was 105 so he got only 201 units 21105 NXT
        // The seller now has 201 less confirmed units but its unconfirmed units did not change because
        // the offer is still valid so he is still committed to sell 500-201=299 units
        AccountCurrencyBalance afterBuySellerBalance = new AccountCurrencyBalance(secretPhrase1, currencyId);
        Assert.assertEquals(new AccountCurrencyBalance(0, 201 * 105, 0, -201),
                afterBuySellerBalance.diff(afterOfferSellerBalance));

        AccountCurrencyBalance afterBuyBuyerBalance = new AccountCurrencyBalance(secretPhrase2, currencyId);
        Assert.assertEquals(new AccountCurrencyBalance(-201*105 - Constants.ONE_NXT, -201*105 - Constants.ONE_NXT, 201, 201),
                afterBuyBuyerBalance.diff(initialBuyerBalance));

        apiCall = new APICall.Builder("getAllExchanges").build();
        JSONObject getAllExchangesResponse = apiCall.invoke();
        Logger.logDebugMessage("getAllExchangesResponse: " + getAllExchangesResponse);
        JSONArray exchanges = (JSONArray)getAllExchangesResponse.get("exchanges");
        JSONObject exchange = (JSONObject) exchanges.get(0);
        Assert.assertEquals("105", exchange.get("rateNQT"));
        Assert.assertEquals("201", exchange.get("units"));
        Assert.assertEquals(currencyId, exchange.get("currency"));
        Assert.assertEquals(initialSellerBalance.getAccountId(), Convert.parseUnsignedLong((String)exchange.get("seller")));
        Assert.assertEquals(initialBuyerBalance.getAccountId(), Convert.parseUnsignedLong((String)exchange.get("buyer")));
    }

    @Test
    public void sellCurrency() {
        String currencyId = TestCurrencyIssuance.issueCurrencyImpl();
        AccountCurrencyBalance initialBuyerBalance = new AccountCurrencyBalance(secretPhrase1, currencyId);
        AccountCurrencyBalance initialSellerBalance = new AccountCurrencyBalance(secretPhrase2, currencyId);

        Assert.assertEquals(100000, initialBuyerBalance.getCurrencyUnits());
        Assert.assertEquals(100000, initialBuyerBalance.getUnconfirmedCurrencyUnits());

        JSONObject publishExchangeOfferResponse = publishExchangeOffer(currencyId);

        generateBlock();

        APICall apiCall = new APICall.Builder("getAllOffers").build();
        JSONObject getAllOffersResponse = apiCall.invoke();
        Logger.logDebugMessage("getAllOffersResponse:" + getAllOffersResponse.toJSONString());
        JSONArray offer = (JSONArray)getAllOffersResponse.get("openOffers");
        Assert.assertEquals(publishExchangeOfferResponse.get("transaction"), ((JSONObject)offer.get(0)).get("offer"));

        // The buy offer reduces the unconfirmed balance but does not change the confirmed balance
        // The sell offer reduces the unconfirmed currency units and confirmed units
        AccountCurrencyBalance afterOfferBuyerBalance = new AccountCurrencyBalance(secretPhrase1, currencyId);
        Assert.assertEquals(new AccountCurrencyBalance(-1000 * 95 - Constants.ONE_NXT, -Constants.ONE_NXT, -500, 0),
                afterOfferBuyerBalance.diff(initialBuyerBalance));

        // We now transfer 2000 units to the 2nd account so that this account can sell them for NXT
        apiCall = new APICall.Builder("transferCurrency").
                secretPhrase(secretPhrase1).feeNQT(Constants.ONE_NXT).
                param("currency", currencyId).
                param("recipient", Convert.toUnsignedLong(initialSellerBalance.getAccountId())).
                param("units", "2000").
                build();
        apiCall.invoke();
        generateBlock();

        AccountCurrencyBalance afterTransferBuyerBalance = new AccountCurrencyBalance(secretPhrase1, currencyId);
        Assert.assertEquals(new AccountCurrencyBalance(-Constants.ONE_NXT, -Constants.ONE_NXT, -2000, -2000),
                afterTransferBuyerBalance.diff(afterOfferBuyerBalance));

        AccountCurrencyBalance afterTransferSellerBalance = new AccountCurrencyBalance(secretPhrase2, currencyId);
        Assert.assertEquals(new AccountCurrencyBalance(0, 0, 2000, 2000),
                afterTransferSellerBalance.diff(initialSellerBalance));

        // sell at rate lower than buy offer results in selling at buy offer rate (95)
        apiCall = new APICall.Builder("currencySell").
                secretPhrase(secretPhrase2).feeNQT(Constants.ONE_NXT).
                param("currency", currencyId).
                param("rateNQT", "" + 90).
                param("units", "200").
                build();
        JSONObject currencyExchangeResponse = apiCall.invoke();
        Logger.logDebugMessage("currencyExchangeResponse:" + currencyExchangeResponse);
        generateBlock();

        // the seller receives 200*95=19000 for 200 units
        AccountCurrencyBalance afterBuyBuyerBalance = new AccountCurrencyBalance(secretPhrase1, currencyId);
        Assert.assertEquals(new AccountCurrencyBalance(0, -19000, 0, 200),
                afterBuyBuyerBalance.diff(afterTransferBuyerBalance));

        AccountCurrencyBalance afterBuySellerBalance = new AccountCurrencyBalance(secretPhrase2, currencyId);
        Assert.assertEquals(new AccountCurrencyBalance(19000-Constants.ONE_NXT, 19000-Constants.ONE_NXT, -200, -200),
                afterBuySellerBalance.diff(afterTransferSellerBalance));

        apiCall = new APICall.Builder("getAllExchanges").build();
        JSONObject getAllExchangesResponse = apiCall.invoke();
        Logger.logDebugMessage("getAllExchangesResponse: " + getAllExchangesResponse);
        JSONArray exchanges = (JSONArray)getAllExchangesResponse.get("exchanges");
        JSONObject exchange = (JSONObject) exchanges.get(0);
        Assert.assertEquals("95", exchange.get("rateNQT"));
        Assert.assertEquals("200", exchange.get("units"));
        Assert.assertEquals(currencyId, exchange.get("currency"));
        Assert.assertEquals(initialSellerBalance.getAccountId(), Convert.parseUnsignedLong((String) exchange.get("seller")));
        Assert.assertEquals(initialBuyerBalance.getAccountId(), Convert.parseUnsignedLong((String)exchange.get("buyer")));
    }

    private JSONObject publishExchangeOffer(String currencyId) {
        APICall apiCall = new APICall.Builder("publishExchangeOffer").
                secretPhrase(secretPhrase1).feeNQT(Constants.ONE_NXT).
                param("deadline", "1440").
                param("currency", currencyId).
                param("buyRateNQT", "" + 95). // buy currency for NXT
                param("sellRateNQT", "" + 105). // sell currency for NXT
                param("totalBuyLimit", "10000").
                param("totalSellLimit", "5000").
                param("initialBuySupply", "1000").
                param("initialSellSupply", "500").
                param("expirationHeight", "" + Integer.MAX_VALUE).
                build();

        JSONObject publishExchangeOfferResponse = apiCall.invoke();
        Logger.logDebugMessage("publishExchangeOfferResponse: " + publishExchangeOfferResponse.toJSONString());
        return publishExchangeOfferResponse;
    }


}
