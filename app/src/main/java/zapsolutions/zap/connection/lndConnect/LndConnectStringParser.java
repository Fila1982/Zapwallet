package zapsolutions.zap.connection.lndConnect;

import com.google.common.io.BaseEncoding;

import java.net.URI;
import java.net.URISyntaxException;

import zapsolutions.zap.connection.CustomSSLSocketFactory;
import zapsolutions.zap.connection.LndConnectionConfig;
import zapsolutions.zap.util.ZapLog;

/**
 * This class parses a lndconnect which is defined in this project:
 * https://github.com/LN-Zap/lndconnect
 * <p>
 * A lndconnect string consists of the following parts:
 * lndconnect://<HOST>:<PORT>?cert=<certificate_encoded_as_base64url>&macaroon=<macaroon_encoded_as_base64url>
 * <p>
 * Note: The certificate is not mandatory. For cases like BTCPay server where another certificate is used, this can be omitted.
 * <p>
 * The parser returns an object containing the desired data or an descriptive error.
 */
public class LndConnectStringParser {

    private static final String LOG_TAG = "LND connect string parser";

    public static final int ERROR_INVALID_CONNECT_STRING = 0;
    public static final int ERROR_NO_MACAROON = 1;
    public static final int ERROR_INVALID_CERTIFICATE = 2;
    public static final int ERROR_INVALID_MACAROON = 3;
    public static final int ERROR_INVALID_HOST_OR_PORT = 4;

    private int mError = -1;
    private String mConnectString;

    private LndConnectionConfig mConnectionConfig;

    public LndConnectStringParser(String connectString){
        mConnectString = connectString;
        mConnectionConfig = new LndConnectionConfig();
    }

    public LndConnectStringParser parse() {

        // validate not null
        if (mConnectString == null) {
            mError = ERROR_INVALID_CONNECT_STRING;
            return this;
        }

        // validate scheme
        if (!mConnectString.toLowerCase().startsWith("lndconnect://")) {
            mError = ERROR_INVALID_CONNECT_STRING;
            return this;
        }

        URI connectURI = null;
        try {
            connectURI = new URI(mConnectString);

            // validate host and port
            if (connectURI.getPort() == -1) {
                mError = ERROR_INVALID_HOST_OR_PORT;
                return this;
            }

            String cert = null;
            String macaroon = null;

            // fetch params
            if (connectURI.getQuery() != null) {
                String[] valuePairs = connectURI.getQuery().split("&");

                for (String pair : valuePairs) {
                    String[] param = pair.split("=");
                    if (param.length > 1) {
                        if (param[0].equals("cert")) {
                            cert = param[1];
                        }
                        if (param[0].equals("macaroon")) {
                            macaroon = param[1];
                        }
                    }
                }

                // validate cert (Certificate is not mandatory for BTCPay server for example, therefore null is valid)
                if (cert != null) {
                    try {
                        byte[] certificateBytes = BaseEncoding.base64Url().decode(cert);
                        try {
                            CustomSSLSocketFactory.create(certificateBytes);
                        } catch (RuntimeException e) {

                            ZapLog.debug(LOG_TAG, "certificate creation failed");
                            mError = ERROR_INVALID_CERTIFICATE;
                            return this;
                        }
                    } catch (IllegalArgumentException e) {
                        ZapLog.debug(LOG_TAG, "cert decoding failed");
                        mError = ERROR_INVALID_CERTIFICATE;
                        return this;
                    }
                }

                // validate macaroon if everything was valid so far
                if (macaroon == null) {
                    ZapLog.debug(LOG_TAG, "lnd connect string does not include a macaroon");
                    mError = ERROR_NO_MACAROON;
                    return this;
                } else {
                    try {
                        BaseEncoding.base64Url().decode(macaroon);
                    } catch (IllegalArgumentException e) {
                        ZapLog.debug(LOG_TAG, "macaroon decoding failed");

                        mError = ERROR_INVALID_MACAROON;
                        return this;
                    }
                }

                // everything is ok, initiate connection
                mConnectionConfig.setHost(connectURI.getHost());
                mConnectionConfig.setPort(connectURI.getPort());
                mConnectionConfig.setCert(cert);
                mConnectionConfig.setMacaroon(macaroon);

                return this;

            } else {
                ZapLog.debug(LOG_TAG, "Connect URI has no parameters");
                mError = ERROR_INVALID_CONNECT_STRING;
                return this;
            }

        } catch (URISyntaxException e) {
            ZapLog.debug(LOG_TAG, "URI could not be parsed");
            mError = ERROR_INVALID_CONNECT_STRING;
            return this;
        }
    }

    public boolean hasError() {
        if (mError > -1) {
            return true;
        } else {
            return false;
        }
    }

    public int getError(){
        return mError;
    }

    public LndConnectionConfig getConnectionConfig() {
        return mConnectionConfig;
    }
}
