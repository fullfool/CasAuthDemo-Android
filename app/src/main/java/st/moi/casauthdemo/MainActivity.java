package st.moi.casauthdemo;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.browser.BrowserWhitelist;
import net.openid.appauth.browser.VersionedBrowserMatcher;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String AUTH_ENDPOINT = "https://apiv2.twitcasting.tv/oauth2/authorize";
    private static final String TOKEN_ENDPOINT = "https://apiv2.twitcasting.tv/oauth2/access_token";

    private static final String CLIENT_ID = "17212538.135ea33b37f2c5ba4c4e44707205d47125e9aefef0ee68a592673bad05231a36";
    private static final String CALLBACK_URL = "st.moi.apps.sampleapp://oauthredirect/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.btn_auth);
        button.setOnClickListener(this);
    }

    @SuppressWarnings("deprecation")
    private int getColorCompat(@ColorRes int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(color);
        } else {
            return getResources().getColor(color);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_auth:
                performAuthorizationRequest(this, new AuthState());
                break;
        }
    }

    private void performAuthorizationRequest(Context context, AuthState authState) {
        AuthorizationServiceConfiguration serviceConfig = new AuthorizationServiceConfiguration(
                Uri.parse(AUTH_ENDPOINT),
                Uri.parse(TOKEN_ENDPOINT),
                null);

        AuthorizationRequest authRequest = new AuthorizationRequest.Builder(
                serviceConfig,
                CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(CALLBACK_URL))
                .build();

        AppAuthConfiguration appAuthConfig = new AppAuthConfiguration.Builder()
                .setBrowserMatcher(new BrowserWhitelist(
                        VersionedBrowserMatcher.CHROME_BROWSER,
                        VersionedBrowserMatcher.CHROME_CUSTOM_TAB
//                        VersionedBrowserMatcher.FIREFOX_BROWSER
                ))
                .build();

        AuthorizationService authService = new AuthorizationService(context, appAuthConfig);
        authService.performAuthorizationRequest(
                authRequest,
                TokenActivity.createPostAuthorizationIntent(context, authRequest, authState),
                authService.createCustomTabsIntentBuilder()
                        .setToolbarColor(getColorCompat(R.color.colorPrimary))
                        .build()
        );
    }
}
