package st.moi.casauthdemo;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.ClientAuthentication;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TokenActivity extends AppCompatActivity {
    private static final String TAG = TokenActivity.class.getSimpleName();
    private static final String KEY_AUTH_STATE = "authState";
    private static final String EXTRA_AUTH_STATE = "authState";
    private static final String EXTRA_AUTH_REQUEST = "extra_auth_request";

    private static final String API_VERSION = "2.0";
    private static final String URL_VERIFY_CREDENTIALS = "https://apiv2.twitcasting.tv/verify_credentials";
    private AuthorizationService mAuthService;
    private AuthState mAuthState;

    static PendingIntent createPostAuthorizationIntent(@NonNull Context context,
                                                       @NonNull AuthorizationRequest request,
                                                       @NonNull AuthState authState) {
        Intent intent = new Intent(context, TokenActivity.class);
        intent.putExtra(EXTRA_AUTH_STATE, authState.jsonSerializeString());
        intent.putExtra(EXTRA_AUTH_REQUEST, request.jsonSerializeString());
        return PendingIntent.getActivity(context, request.hashCode(), intent, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_token);

        mAuthService = new AuthorizationService(this);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_AUTH_STATE)) {
                try {
                    mAuthState = AuthState.jsonDeserialize(
                            savedInstanceState.getString(KEY_AUTH_STATE));
                } catch (JSONException ex) {
                    Log.e(TAG, "Malformed authorization JSON saved", ex);
                }
            }
        }

        if (mAuthState == null) {
            Intent intent = getIntent();
            mAuthState = getAuthStateFromIntent(intent);
            AuthorizationResponse response = AuthorizationResponse.fromIntent(intent);
            AuthorizationException ex = AuthorizationException.fromIntent(intent);
            mAuthState.update(response, ex);

            if (response != null) {
                Log.d(TAG, "Received AuthorizationResponse.");
                showSnackbar(R.string.exchange_notification);
                exchangeAuthorizationCode(response);
            } else {
                Log.i(TAG, "Authorization failed: " + ex);
                showSnackbar(R.string.authorization_failed);
            }
        }
        refreshUi();
    }

    private AuthState getAuthStateFromIntent(Intent intent) {
        if (!intent.hasExtra(EXTRA_AUTH_STATE)) {
            throw new IllegalArgumentException("The AuthState instance is missing in the intent.");
        }
        try {
            return AuthState.jsonDeserialize(intent.getStringExtra(EXTRA_AUTH_STATE));
        } catch (JSONException ex) {
            Log.e(TAG, "Malformed AuthState JSON saved", ex);
            throw new IllegalArgumentException("The AuthState instance is missing in the intent.");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (mAuthState != null) {
            state.putString(KEY_AUTH_STATE, mAuthState.jsonSerializeString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAuthService.dispose();
    }

    @MainThread
    private void showSnackbar(@StringRes int messageId) {
        Snackbar.make(findViewById(R.id.coordinator),
                getResources().getString(messageId),
                Snackbar.LENGTH_SHORT)
                .show();
    }

    private void exchangeAuthorizationCode(AuthorizationResponse authorizationResponse) {
        performTokenRequest(authorizationResponse.createTokenExchangeRequest());
    }

    private void performTokenRequest(TokenRequest request) {
        ClientAuthentication clientAuthentication;
        try {
            clientAuthentication = mAuthState.getClientAuthentication();
        } catch (ClientAuthentication.UnsupportedAuthenticationMethod ex) {
            Log.d(TAG, "Token request cannot be made, client authentication for the token "
                    + "endpoint could not be constructed (%s)", ex);
            return;
        }

        mAuthService.performTokenRequest(
                request,
                clientAuthentication,
                new AuthorizationService.TokenResponseCallback() {
                    @Override
                    public void onTokenRequestCompleted(
                            @Nullable TokenResponse tokenResponse,
                            @Nullable AuthorizationException ex) {
                        receivedTokenResponse(tokenResponse, ex);
                    }
                });
    }

    private void receivedTokenResponse(
            @Nullable TokenResponse tokenResponse,
            @Nullable AuthorizationException authException) {
        Log.d(TAG, "Token request complete");
        mAuthState.update(tokenResponse, authException);
        showSnackbar((tokenResponse != null)
                ? R.string.exchange_complete
                : R.string.refresh_failed);
        refreshUi();
    }

    private void refreshUi() {
        TextView accessTokenInfoView = (TextView) findViewById(R.id.access_token_info);
        if (mAuthState.getAccessToken() == null) {
            accessTokenInfoView.setText(R.string.no_access_token_returned);
        } else {
            Long expiresAt = mAuthState.getAccessTokenExpirationTime();
            String expiryStr;
            if (expiresAt == null) {
                expiryStr = getResources().getString(R.string.unknown_expiry);
            } else {
                expiryStr = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL)
                        .format(new Date(expiresAt));
            }
            String tokenInfo = String.format(
                    getResources().getString(R.string.access_token_expires_at),
                    expiryStr);
            accessTokenInfoView.setText(tokenInfo);
        }

        final Button verifyButton = (Button) findViewById(R.id.verify_credentials);
        verifyButton.setVisibility(mAuthState.isAuthorized()
                ? View.VISIBLE
                : View.GONE);
        verifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                verifyCredentials();
            }
        });
    }

    private void verifyCredentials() {
        // 現在はrefresh token無いので、失効した時は失敗します
        mAuthState.performActionWithFreshTokens(mAuthService, new AuthState.AuthStateAction() {
            @Override
            public void execute(@Nullable String accessToken, @Nullable String idToken, @Nullable AuthorizationException e) {
                if (e != null) {
                    Log.e(TAG, "Token refresh failed when verify credentials", e);
                    return;
                }
                new AsyncTask<String, Void, JSONObject>() {
                    @Override
                    protected JSONObject doInBackground(String... strings) {
                        OkHttpClient client = new OkHttpClient();
                        Request request = new Request.Builder()
                                .url(URL_VERIFY_CREDENTIALS)
                                .addHeader("Authorization", String.format("Bearer %s", strings[0]))
                                .addHeader("X-Api-Version", API_VERSION)
                                .addHeader("Accept-Encoding", "gzip")
                                .build();
                        try {
                            Response response = client.newCall(request).execute();
                            String jsonBody = response.body().string();
                            return new JSONObject(jsonBody);
                        } catch (IOException | JSONException e1) {
                            e1.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(JSONObject userInfo) {
                        if (userInfo != null) {
                            Log.d(TAG, "userInfo:" + userInfo);
                            TextView textView = (TextView) findViewById(R.id.userinfo_json);
                            textView.setText(userInfo.toString());
                        }
                    }
                }.execute(accessToken);
            }
        });
    }

}
