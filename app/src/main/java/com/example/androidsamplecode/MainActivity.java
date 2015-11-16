package com.example.androidsamplecode;

import java.io.Console;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity implements View.OnClickListener
{
	// sets activity members

	private static final String RECORDING_URL = "https://alphav3.beyondverbal.com/v1/recording/";

	private static final String Auth_URL = "https://token.beyondverbal.com/token";//https://token.beyondverbal.com/";//token


	private static final String APIKey ="20bb49be-3979-485c-a46d-ad257770c2f7";
	private Header access_token;
	private String recordingid ;
	private Button upstreamButton;
	private Button sendFileButton;
	private TextView statusContent;
	private TextView responseContentTextView;
	private TextView txtWait;
	private ProgressDialog progressDialog;
	private ResponseHolder responseHolder;


	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		responseHolder = new ResponseHolder();
		initViews();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			WebView.setWebContentsDebuggingEnabled(true);

		}
	}

	/**
	 * Initialize all views from xml resource
	 */
	private void initViews()
	{
		upstreamButton = initButton(R.id.get_upstream_button);
		sendFileButton = initButton(R.id.send_file_button);
		statusContent = initTextView(R.id.status_content_textView);

		txtWait = initTextView(R.id.txtWait);
		responseContentTextView = initTextView(R.id.response_content_textView);
		upstreamButton.setOnClickListener(this);
		sendFileButton.setOnClickListener(this);
		//sendFileButton.setEnabled(false);
	}

	private Button initButton(int buttonId)
	{
		return (Button) findViewById(buttonId);
	}
	private SeekBar initSeekBar(int progressId)
	{
		return (SeekBar) findViewById(progressId);
	}
	private TextView initTextView(int textViewId)
	{
		return (TextView) findViewById(textViewId);
	}
	/**
	 * Post to server according to which button was pressed
	 *
	 * @param buttonId
	 *            the id of the button pressed
	 * @return ResponseHolder
	 */

	private ResponseHolder postByAction(int buttonId)
	{
		//System.setProperty("http.proxyHost", "127.0.0.1");
		//System.setProperty("http.proxyPort", "8888");

		HttpActivity httpa = new HttpActivity();

		switch (buttonId)
		{
			case R.id.get_upstream_button:
				getToken();
				responseHolder = httpa.doPost(RECORDING_URL + "start", access_token, getEntityForUpstream());

				if (responseHolder.content != null){
					recordingid = getRecordingid(responseHolder.content);
					GoStream();

				}
				break;
			case R.id.send_file_button:
				getToken();
				HttpEntity entity = getEntityForUpstream();
				responseHolder = httpa.doPost(RECORDING_URL + "start", access_token, entity);

				if (responseHolder.content != null){
					recordingid = getRecordingid(responseHolder.content);
					responseHolder = httpa.doPost(RECORDING_URL + recordingid, access_token, getEntityForSendFile());
				}


		}
		return responseHolder;
	}
	/******
	 *
	 * Create Asyncronic Post(Stream file)
	 * **/
	public void GoStream() {


		txtWait.post(new Runnable() {
			public void run() {
				txtWait.setText("Wait");
			}
		});

		Thread stream =new Thread(new Runnable() {
			@Override
			public void run() {

				HttpActivity httpac = new HttpActivity();
				final ResponseHolder hol = httpac.doPost(RECORDING_URL + recordingid, access_token, getEntityForSendFile());


				responseContentTextView.post(new Runnable() {
					public void run() {
						CharSequence text = responseContentTextView.getText();
						responseContentTextView.setText("Full analysis::::"+hol.content+text);
					}
				});

			}
		});

		stream.start();
		//****
		// When post is sended file anylize file parts (Asyncronic send requests for analysis with FromMs milisecond from start file )
		// **/
		Analyze();
	}
	private long FromMs = 0;

	public void Analyze(){

		final Timer myTimer = new Timer();
		long delay = 0;
		long period=5000;
		//final Handler uiHandler = new Handler();

		myTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				HttpActivity httpac = new HttpActivity();
				try{

					txtWait.post(new Runnable() {
						public void run() {
							txtWait.append(".");
						}
					});

					final ResponseHolder hol = httpac.doGet(RECORDING_URL + recordingid + "/analysis?fromMs=" + FromMs, access_token);

					String status = getJName(hol.content, "status");
					String sesionStatus = getsesionStatus(hol.content);
					String f = getDuration(hol.content);
					if (status.equals("success")) {
						FromMs = Long.parseLong(f.replace(".0", ""));
					}
					if (sesionStatus!=null && sesionStatus.equals("Done")) {
						myTimer.cancel();
						txtWait.post(new Runnable() {
							public void run() {
								txtWait.setText("");
							}
						});
					}

					responseContentTextView.post(new Runnable() {
						public void run() {
//							CharSequence text = responseContentTextView.getText();
//							responseContentTextView.setText("\n....\n"+hol.content+text);
							responseContentTextView.append("\n....\n"+hol.content);
						}
					});
				}
				catch (Exception e){
					e.printStackTrace();
				}

			}
		},delay,period);


	}

	protected void ResponseResult(ResponseHolder rs){

		statusContent.setText(rs.responseString);
		if (rs.content != null)
		{
			//CharSequence text = responseContentTextView.getText();
			//responseContentTextView.setText("\n-----------------\n" + rs.content + text);
			responseContentTextView.append("\n-----------------\n" + rs.content);
		}

	}
	private String getsesionStatus(String response){
		if (response == null)
			return null;
		try {
			JSONObject json = new JSONObject(response);
			String duration = json.getJSONObject("result").getString("sessionStatus");

			return duration;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}
	private String getDuration(String response){
		if (response == null)
			return null;
		try {
			JSONObject json = new JSONObject(response);
			String duration = json.getJSONObject("result").getString("duration");

			return duration;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}
	private String getJName(String response,String name){
		if (response == null)
			return null;
		try {
			JSONObject json = new JSONObject(response);
			String recordingid = json.getString(name);

			return recordingid;
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return null;

	}
	private String getRecordingid(String response) {
		if (response == null)
			return null;
		try {
			JSONObject json = new JSONObject(response);
			String recordingid = json.getString("recordingId");

			return recordingid;
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Called when one of the buttons is pressed getUpstream or SendFile
	 * 
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 */
	@Override
	public void onClick(View v)
	{
		new ServerConnection().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, v.getId());
	}


	/**
	 * Creates background thread to connect to server in order to avoid blocking
	 * UI thread
	 * 
	 */
	private class ServerConnection extends AsyncTask<Integer, Void, ResponseHolder>
	{

		//ProgressDialog progressDialog;

		@Override
		protected void onPreExecute()
		{
			progressDialog = new ProgressDialog(MainActivity.this);
			progressDialog.setMessage("please wait...");
			progressDialog.show();
			super.onPreExecute();
		}

		@Override
		protected ResponseHolder doInBackground(Integer... params)
		{
			int buttonId = params[0];
			responseHolder.actionId = buttonId;
			return postByAction(buttonId);
		}

		@Override
		protected void onPostExecute(ResponseHolder responseHolder)
		{
			statusContent.setText(responseHolder.responseString);
			if (responseHolder.content != null)
			{
				//setButtonByAction(responseHolder.actionId);
				CharSequence text = responseContentTextView.getText();
				responseContentTextView.setText(text + responseHolder.content);
			}

			progressDialog.dismiss();
			super.onPostExecute(responseHolder);
		}
	}

//	/**
//	 * Disable send_file_button until receiving upstream url from server
//	 * @param actionId the button id
//	 */
//	private void setButtonByAction(int actionId)
//	{
//		boolean statusButton = false;
//		switch (actionId)
//		{
//		case R.id.get_upstream_button:
//			statusButton = true;
//			break;
//
//		case R.id.send_file_button:
//			statusButton = false;
//			break;
//
//		default:
//			break;
//		}
//		sendFileButton.setEnabled(statusButton);
//	}

	/**
	 * @return the WAV file from local resource
	 */
	private HttpEntity getEntityForSendFile()
	{

		// Fetches file from local resources.
		InputStream raw = getResources().openRawResource(R.raw.sample);
		InputStreamEntity reqEntity = new InputStreamEntity(raw, -1);
		return reqEntity;
	}

	/**
	 * @return the configuration data for get upstream url
	 */
	private HttpEntity getEntityForUpstream()
	{
		StringEntity se = null;
		try
		{
			se = new StringEntity(getConfigData());
			se.setContentType("application/json; charset=UTF-8");
		} catch (UnsupportedEncodingException e)
		{
			e.printStackTrace();
		}
		return se;
	}


	/**
	 * Create the https client with all configuration settings
	 *
	 * @return
	 */

	private void getToken() {
		if(access_token!=null)
			return;
		HttpActivity httpa = new HttpActivity();
		String jsonToken = httpa.doPost(Auth_URL, null, getEntityForAccessToken()).content;
		if (jsonToken == null)
			return ;
		JSONObject jsonObject;
		Header header = null;
		try {
			jsonObject = new JSONObject(jsonToken);
			header = new BasicHeader("Authorization",
					jsonObject.getString("token_type")+" "+jsonObject.getString("access_token"));
			Log.i("header", header.getName() + "  " + header.getValue());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		access_token = header;
	}
	private HttpEntity getEntityForAccessToken() {

		String body = String.format("apikey=%s&grant_type=%s", APIKey, "client_credentials");

		StringEntity se = null;
		try {
			se = new StringEntity(body);
			se.setContentType("Content-Type:application/x-www-form-urlencoded");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return se;

	}
	/**
	 * Sets configuration data for POST to server to receive upstream url
	 *
	 * @return
	 */
	private String getConfigData()
	{
		try
		{
			// Instantiate a JSON Object and fill with Configuration Data
			// (Currently set to Auto Config)
			JSONObject inner_json = new JSONObject();
			inner_json.put("type", "WAV");
			inner_json.put("channels", 1);
			inner_json.put("sample_rate", 0);
			inner_json.put("bits_per_sample", 0);
			inner_json.put("auto_detect", true);
			JSONObject json = new JSONObject();
			json.put("data_format", inner_json);

			return json.toString();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
		return null;
	}


}
