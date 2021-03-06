package io.openvidu.openvidu_android.activities;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.openvidu.openvidu_android.R;
import io.openvidu.openvidu_android.fragments.PermissionsDialogFragment;
import io.openvidu.openvidu_android.openvidu.LocalParticipant;
import io.openvidu.openvidu_android.openvidu.RemoteParticipant;
import io.openvidu.openvidu_android.openvidu.Session;
import io.openvidu.openvidu_android.utils.CustomHttpClient;
import io.openvidu.openvidu_android.websocket.CustomWebSocket;
import java.io.IOException;
import java.util.Random;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

public class SessionActivity extends AppCompatActivity {

  private static final int MY_PERMISSIONS_REQUEST_CAMERA = 100;
  private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 101;
  private static final int MY_PERMISSIONS_REQUEST = 102;
  private final String TAG = "SessionActivity";
  @BindView(R.id.views_container)
  LinearLayout viewsContainer;
  @BindView(R.id.start_finish_call)
  Button startFinishCall;
  @BindView(R.id.session_name)
  EditText sessionName;
  @BindView(R.id.participant_name)
  EditText participantName;
  @BindView(R.id.openvidu_url)
  EditText openviduUrl;
  @BindView(R.id.openvidu_secret)
  EditText openviduSecret;
  @BindView(R.id.local_gl_surface_view)
  SurfaceViewRenderer localVideoView;
  @BindView(R.id.main_participant)
  TextView mainParticipant;
  @BindView(R.id.peer_container)
  FrameLayout peerContainer;

  private String OPENVIDU_URL;
  private String OPENVIDU_SECRET;
  private Session session;
  private CustomHttpClient httpClient;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    setContentView(R.layout.activity_main);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    askForPermissions();
    ButterKnife.bind(this);
    Random random = new Random();
    int randomIndex = random.nextInt(100);
    participantName.setText(participantName.getText().append(String.valueOf(randomIndex)));
  }

  public void askForPermissions() {
    if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) &&
        (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED)) {
      ActivityCompat.requestPermissions(this,
          new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
          MY_PERMISSIONS_REQUEST);
    } else if (ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
          new String[]{Manifest.permission.RECORD_AUDIO},
          MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
    } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
          new String[]{Manifest.permission.CAMERA},
          MY_PERMISSIONS_REQUEST_CAMERA);
    }
  }

  public void buttonPressed(View view) {
    if (startFinishCall.getText().equals(getResources().getString(R.string.hang_up))) {
      // Already connected to a session
      leaveSession();
      return;
    }
    if (arePermissionGranted()) {
      initViews();
      viewToConnectingState();

      OPENVIDU_URL = openviduUrl.getText().toString();
      OPENVIDU_SECRET = openviduSecret.getText().toString();
      httpClient = new CustomHttpClient(OPENVIDU_URL, "Basic " + android.util.Base64
          .encodeToString(("OPENVIDUAPP:" + OPENVIDU_SECRET).getBytes(),
              android.util.Base64.DEFAULT).trim());

      String sessionId = sessionName.getText().toString();
      getToken(sessionId);
    } else {
      DialogFragment permissionsFragment = new PermissionsDialogFragment();
      permissionsFragment.show(getSupportFragmentManager(), "Permissions Fragment");
    }
  }

  private void getToken(String sessionId) {
    try {
      // Session Request
      RequestBody sessionBody = RequestBody
          .create(MediaType.parse("application/json; charset=utf-8"),
              "{\"customSessionId\": \"" + sessionId + "\"}");
      this.httpClient.httpCall("/openvidu/api/sessions", "POST", "application/json", sessionBody,
          new Callback() {

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response)
                throws IOException {
              Log.d(TAG, "responseString: " + response.body().string());

              // Token Request
              RequestBody tokenBody = RequestBody
                  .create(MediaType.parse("application/json; charset=utf-8"), "{}");
              httpClient.httpCall("/openvidu/api/sessions/" + sessionId + "/connection", "POST",
                  "application/json", tokenBody, new Callback() {

                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) {
                      String responseString = null;
                      try {
                        responseString = response.body().string();
                      } catch (IOException e) {
                        Log.e(TAG, "Error getting body", e);
                      }
                      Log.d(TAG, "responseString2: " + responseString);
                      JSONObject tokenJsonObject = null;
                      String token = null;
                      try {
                        tokenJsonObject = new JSONObject(responseString);
                        token = tokenJsonObject.getString("token");
                      } catch (JSONException e) {
                        e.printStackTrace();
                      }
                      getTokenSuccess(token, sessionId);
                    }

                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                      Log.e(TAG, "Error POST /api/tokens", e);
                      connectionError();
                    }
                  });
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
              Log.e(TAG, "Error POST /api/sessions", e);
              connectionError();
            }
          });
    } catch (IOException e) {
      Log.e(TAG, "Error getting token", e);
      e.printStackTrace();
      connectionError();
    }
  }

  private void getTokenSuccess(String token, String sessionId) {
    // Initialize our session
    session = new Session(sessionId, token, viewsContainer, this);

    // Initialize our local participant and start local camera
    String participantName = this.participantName.getText().toString();
    LocalParticipant localParticipant = new LocalParticipant(participantName, session,
        this.getApplicationContext(), localVideoView);
    localParticipant.startCamera();
    runOnUiThread(() -> {
      // Update local participant view
      mainParticipant.setText(this.participantName.getText().toString());
      mainParticipant.setPadding(20, 3, 20, 3);
    });

    // Initialize and connect the websocket to OpenVidu Server
    startWebSocket();
  }

  private void startWebSocket() {
    CustomWebSocket webSocket = new CustomWebSocket(session, OPENVIDU_URL, this);
    webSocket.execute();
    session.setWebSocket(webSocket);
  }

  private void connectionError() {
    Runnable myRunnable = () -> {
      Toast toast = Toast.makeText(this, "Error connecting to " + OPENVIDU_URL, Toast.LENGTH_LONG);
      toast.show();
      viewToDisconnectedState();
    };
    new Handler(this.getMainLooper()).post(myRunnable);
  }

  private void initViews() {
    EglBase rootEglBase = EglBase.create();
    localVideoView.init(rootEglBase.getEglBaseContext(), null);
    localVideoView.setMirror(true);
    localVideoView.setEnableHardwareScaler(true);
    localVideoView.setZOrderMediaOverlay(true);
  }

  public void viewToDisconnectedState() {
    runOnUiThread(() -> {
      localVideoView.clearImage();
      localVideoView.release();
      startFinishCall.setText(getResources().getString(R.string.start_button));
      startFinishCall.setEnabled(true);
      openviduUrl.setEnabled(true);
      openviduUrl.setFocusableInTouchMode(true);
      openviduSecret.setEnabled(true);
      openviduSecret.setFocusableInTouchMode(true);
      sessionName.setEnabled(true);
      sessionName.setFocusableInTouchMode(true);
      participantName.setEnabled(true);
      participantName.setFocusableInTouchMode(true);
      mainParticipant.setText(null);
      mainParticipant.setPadding(0, 0, 0, 0);
    });
  }

  public void viewToConnectingState() {
    runOnUiThread(() -> {
      startFinishCall.setEnabled(false);
      openviduUrl.setEnabled(false);
      openviduUrl.setFocusable(false);
      openviduSecret.setEnabled(false);
      openviduSecret.setFocusable(false);
      sessionName.setEnabled(false);
      sessionName.setFocusable(false);
      participantName.setEnabled(false);
      participantName.setFocusable(false);
    });
  }

  public void viewToConnectedState() {
    runOnUiThread(() -> {
      startFinishCall.setText(getResources().getString(R.string.hang_up));
      startFinishCall.setEnabled(true);
    });
  }

  public void createRemoteParticipantVideo(final RemoteParticipant remoteParticipant) {
    Handler mainHandler = new Handler(this.getMainLooper());
    Runnable myRunnable = () -> {
      View rowView = this.getLayoutInflater().inflate(R.layout.peer_video, null);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.setMargins(0, 0, 0, 20);
      rowView.setLayoutParams(lp);
      int rowId = View.generateViewId();
      rowView.setId(rowId);
      viewsContainer.addView(rowView);
      SurfaceViewRenderer videoView = (SurfaceViewRenderer) ((ViewGroup) rowView).getChildAt(0);
      remoteParticipant.setVideoView(videoView);
      videoView.setMirror(false);
      EglBase rootEglBase = EglBase.create();
      videoView.init(rootEglBase.getEglBaseContext(), null);
      videoView.setZOrderMediaOverlay(true);
      View textView = ((ViewGroup) rowView).getChildAt(1);
      remoteParticipant.setParticipantNameText((TextView) textView);
      remoteParticipant.setView(rowView);

      remoteParticipant.getParticipantNameText().setText(remoteParticipant.getParticipantName());
      remoteParticipant.getParticipantNameText().setPadding(20, 3, 20, 3);
    };
    mainHandler.post(myRunnable);
  }

  public void setRemoteMediaStream(MediaStream stream, final RemoteParticipant remoteParticipant) {
    final VideoTrack videoTrack = stream.videoTracks.get(0);
    videoTrack.addSink(remoteParticipant.getVideoView());
    runOnUiThread(() -> {
      remoteParticipant.getVideoView().setVisibility(View.VISIBLE);
    });
  }

  public void leaveSession() {
    if (this.session != null) {
      this.session.leaveSession();
    }
    if (this.httpClient != null) {
      this.httpClient.dispose();
    }
    viewToDisconnectedState();
  }

  private boolean arePermissionGranted() {
    return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_DENIED) &&
        (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_DENIED);
  }

  @Override
  protected void onDestroy() {
    leaveSession();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    leaveSession();
    super.onBackPressed();
  }

  @Override
  protected void onStop() {
    leaveSession();
    super.onStop();
  }

}
