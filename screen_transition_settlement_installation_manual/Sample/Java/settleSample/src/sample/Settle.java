package sample;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * サンプルコード
 * 決済開始処理
 *
 * 決済に必要な情報をBitCash決済システムに対してHTTP(S)プロトコルのGETまたはPOSTメソッドを利用して送信します。
 * BitCash決済システムは加盟店様から送信された決済情報を元に決済の準備を行い、
 * 処理結果としてトランザクションIDや決済画面のURLを返却します。
 * 決済システムからの応答はHTTP(S)のレスポンスボディに
 * 「パラメータ名=パラメータ値&パラメータ名=パラメータ値&…」という形式で出力されますので、
 * 応答を受信した加盟店様側ではレスポンスボディの文字列を「&」で分割し、
 * 更に分割したそれぞれの文字列を「=」で分割する事によりトランザクションIDや決済画面のURLを取得する事ができます。
 * @copyright	Copyright (c) 2014 BitCash Inc. All Rights Reserved.
 * @author		BitCash
 * @since		2014/1/10
 *
 * < 免責事項 >
 * ①サンプルプログラムはBitCash決済を導入するための参考として、弊社が十分に動作確認を行った上で提供しておりますが、
 * 加盟店様のご利用環境に よっては利用できない場合があります。
 * ご利用は加盟店様ご自身の責任において行っていただきますようお願いいたします。
 *
 * ②本サンプルプログラムのご利用により発生した、いかなるトラブルや損失・損害等につきましては
 * 一切責任を負わないものとします。
 *
 * ③本サンプルプログラムに不備があった場合でも、弊社は修正およびサポートの義務を負いかねます。
 *
 * ④本サンプルプログラムに関する財産権、所有権、知的財産権、その他一切の権限は、弊社に帰属します。
 */
@WebServlet("/Settle")
public class Settle extends HttpServlet {
	private static final long serialVersionUID = 1L;

    public Settle() {
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		Properties properties;
	    // プロパティファイルパスの取得
	    ServletContext context = this.getServletContext();
	    String path = context.getRealPath("/WEB-INF/bitcash.properties");

	    // プロパティファイルをロード
	    try {
	        InputStream in = new FileInputStream(path);
	        properties = new Properties();
	        properties.load(in);
	        in.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		request.setCharacterEncoding("UTF-8");
	    String itemRating = request.getParameter("itemRating");
	    String itemPrice = request.getParameter("itemPrice");

		/* ビットキャッシュ-決済準備処理用のURL */
		final String PREPARE_SETTLE_URL = properties.getProperty("prepare_settle_url");


	    /* 決済準備処理で接続するURL生成 */
		URL url = new URL(PREPARE_SETTLE_URL);
		HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
		urlConnection.setDoOutput(true);
		// キャッシュを使わないように設定
		urlConnection.setUseCaches(false);
		// リクエストメソッドをPOSTに設定
		urlConnection.setRequestMethod("POST");

		/***
		 *  加盟店様の認証に使用する情報
		 ***/
		String shopId = properties.getProperty("shop_id"); 			// 契約時にお渡しした加盟店ID
		String shopKey = properties.getProperty("shop_key");		// 契約時にお渡しした加盟店キー

		// ビットキャッシュ決済画面の表示有効期限の設定(秒単位で設定。サンプルは10分)。任意の期限を設定してください
		String timeOut = properties.getProperty("timeout");

		// 決済完了通知URL ユーザ様がビットキャッシュ決済画面にてお支払が完了した際、ビットキャッシュより通知を行うURLです
		// 定期実行のみでビットキャッシュからのお支払完了通知が不要の場合は、「none」を指定してください
		String notifyUrl = properties.getProperty("notify_url");

		// お支払完了後、加盟店様サイトへ戻る際のURLです
		String returnUrl = properties.getProperty("return_url");

		// ユーザ様がビットキャッシュでのお支払の前に加盟店様サイトへ戻る際のURLです
		// サンプルでは、商品選択画面を設定しています
		String cancelUrl = properties.getProperty("cancel_url");

		String orderId = "ORDER_" + System.currentTimeMillis();
		// オーダーID(注文番号) サンプルのため、現在日時を設定していますが、加盟店様にてユーザ様の支払情報を特定するIDを設定してください


		// POSTするパラメータの文字列作成
		StringBuilder paramBuilder = new StringBuilder();
		paramBuilder
			.append("shop_id=").append(shopId) 			// 契約時にお渡しした加盟店ID
			.append("&shop_key=").append(shopKey)		// 契約時にお渡しした加盟店キー
			.append("&price=").append(itemPrice)		// お支払金額
			.append("&rating=").append(itemRating)		// 商品のレイティング
			.append("&order_id=").append(orderId)		// オーダーID(注文番号) サンプルのため、現在日時を設定していますが、加盟店様にてユーザ様の支払情報を特定するIDを設定してください
			.append("&timeout=").append(timeOut)
			.append("&notify_url=").append(notifyUrl)
			.append("&return_url=").append(returnUrl)
			.append("&cancel_url=").append(cancelUrl);

		/***
		 * 送信パラメータ出力（証跡として出力しておきます）
		 */
		// 定数
	    final String SEND_FILE_PATH = properties.getProperty("start_settle_send_param_file_path");	// 送信パラメータ出力ファイルパス
		final String SEND_FILE_SEP = ",";					// データ区切り文字
		final String SEND_FILE_LINE_SEP = "\r\n";			// 行区切り
		final String SEND_FILE_ENCODE = "UTF-8";			// エンコードタイプ

		File sendParamFile = new File(SEND_FILE_PATH);

		BufferedWriter sendParamFileBufferedWriter = null;
		try {
			if( !sendParamFile.exists() ) {
				// ファイルが存在しない場合
				sendParamFile.createNewFile();
				sendParamFileBufferedWriter = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(sendParamFile, false), SEND_FILE_ENCODE)
				);
				// ヘッダ部出力
				StringBuilder headerBuilder = new StringBuilder();
				headerBuilder
					.append("shop_id").append(SEND_FILE_SEP)
					.append("shop_key").append(SEND_FILE_SEP)
					.append("price").append(SEND_FILE_SEP)
					.append("rating").append(SEND_FILE_SEP)
					.append("order_id").append(SEND_FILE_SEP)
					.append("timeout").append(SEND_FILE_SEP)
					.append("notify_url").append(SEND_FILE_SEP)
					.append("return_url").append(SEND_FILE_SEP)
					.append("cancel_url")
					.append(SEND_FILE_LINE_SEP);

				// ヘッダ書き込み
				sendParamFileBufferedWriter.write(headerBuilder.toString());


			} else {
				// ファイルが存在する場合
				sendParamFileBufferedWriter = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(sendParamFile, true), SEND_FILE_ENCODE)
				);
			}

			// データ部出力
			StringBuilder dataBuilder = new StringBuilder();
			dataBuilder
				.append(shopId).append(SEND_FILE_SEP)
				.append(shopKey).append(SEND_FILE_SEP)
				.append(itemPrice).append(SEND_FILE_SEP)
				.append(itemRating).append(SEND_FILE_SEP)
				.append(orderId).append(SEND_FILE_SEP)
				.append(timeOut).append(SEND_FILE_SEP)
				.append(notifyUrl).append(SEND_FILE_SEP)
				.append(returnUrl).append(SEND_FILE_SEP)
				.append(cancelUrl)
				.append(SEND_FILE_LINE_SEP);

			// データ書き込み
			sendParamFileBufferedWriter.write(dataBuilder.toString());
		} finally {
			if( sendParamFileBufferedWriter != null ) {
				sendParamFileBufferedWriter.close();
			}
		}

		/***
		 * リクエスト出力
		 */
		// 出力ストリーム作成
		PrintWriter printWriter = new PrintWriter(urlConnection.getOutputStream());
		try {
			// 生成したパラメータをPOST
			printWriter.print(paramBuilder.toString());
	    } finally {
	    	printWriter.close();
		}


		/***
		 * レスポンス取得
		 */
		String postResponseString = new String();
		String tempString;
		// 入力ストリーム作成してPOSTのレスポンス取得
		BufferedReader bufferReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF8"));
		try {
			// 入力ストリームから1行ずつ読み込む
			while ((tempString = bufferReader.readLine()) != null) {
				postResponseString = postResponseString + tempString;
			}
		} finally {
			bufferReader.close();
		}

		// 接続用HttpURLConnection切断
		urlConnection.disconnect();

		// 受信したパラメータの分解
		String[] postResponseParamArray = postResponseString.split("&");
		Map<String, String> paramMap = new HashMap<String, String>();
		for ( int i = 0; i < postResponseParamArray.length; i++ ) {
			String[] param = postResponseParamArray[i].split("=");
			paramMap.put(param[0], param[1]);
		}


		/***
		 * 受信パラメータ出力（証跡として出力しておきます）
		 */
		// 定数
	    final String RECEIVE_FILE_PATH = properties.getProperty("start_settle_receive_param_file_path");	// 受信パラメータ出力ファイルパス
		final String RECEIVE_FILE_SEP = ",";				// 区切り文字
		final String RECEIVE_FILE_LINE_SEP = "\r\n";		// 行区切り
		final String RECEIVE_FILE_ENCODE = "UTF-8";			// エンコードタイプ

		File receiveParamFile = new File(RECEIVE_FILE_PATH);

		BufferedWriter receiveParamFileBufferedWriter = null;
		try {
			if( !receiveParamFile.exists() ) {
				// ファイルが存在しない場合
				receiveParamFile.createNewFile();

				receiveParamFileBufferedWriter = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(receiveParamFile, false), RECEIVE_FILE_ENCODE)
				);
				// ヘッダ部出力
				StringBuilder headerBuilder = new StringBuilder();
				headerBuilder
					.append("status_code").append(RECEIVE_FILE_SEP)
					.append("status_string").append(RECEIVE_FILE_SEP)
					.append("tran_id").append(RECEIVE_FILE_SEP)
					.append("settle_url")
					.append(RECEIVE_FILE_LINE_SEP);

				// ヘッダ書き込み
				receiveParamFileBufferedWriter.write(headerBuilder.toString());

			} else {
				// ファイルが存在する場合
				receiveParamFileBufferedWriter = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(receiveParamFile, true), RECEIVE_FILE_ENCODE)
				);
			}
			// データ部出力
			StringBuilder dataBuilder = new StringBuilder();
			dataBuilder
				.append(paramMap.get("status_code")).append(RECEIVE_FILE_SEP)
				.append(paramMap.get("status_string")).append(RECEIVE_FILE_SEP)
				.append(paramMap.get("tran_id")).append(RECEIVE_FILE_SEP)
				.append(paramMap.get("settle_url"))
				.append(RECEIVE_FILE_LINE_SEP);

			// データ書き込み
			receiveParamFileBufferedWriter.write(dataBuilder.toString());
		} finally {
			if( receiveParamFileBufferedWriter != null ) {
				receiveParamFileBufferedWriter.close();
			}
		}

		if ( paramMap.get("status_string").equals("SUCCESS") ) {
			/*	決済準備処理が成功した場合
			 * 	status_code:"0"				ステータスコード：処理成功
			 *	status_string:"SUCCEESS"	ステータス文字列：処理成功
			 *	tran_id						決済を一意に識別できるトランザクションID
			 *	settle_url					決済画面URL(暗号化トランザクションID付加)
			 */

			/*
			 * ユーザ様のお支払情報を一意に特定するトランザクションIDを返却します。
			 * 問い合わせ、お支払の取消、お支払の確認にも使用いただけますので、加盟店様システム側で保管してください。
			 * */
			// paramMap.get("tran_id")


			// 決済画面URL(settleUrl=)後のパラメータ(決済画面URL)へリクエストをリダイレクトします
			// ※パラメータはエンコードされているためデコードが必要です
			response.setContentType("text/html;charset=UTF-8");
			String settleUrl = URLDecoder.decode(paramMap.get("settle_url"),"utf-8");
			response.sendRedirect(response.encodeRedirectURL(settleUrl));
		} else {
			/*	決済準備処理が失敗した場合
			 *	status_code:"100",status_string:"INVALID_SHOP_ID"			加盟店IDが不正な場合
			 *	status_code:"101",status_string:"INVALID_SHOP_KEY"			加盟店キーが不正な場合
			 *	status_code:"102",status_string:"INVALID_PRICE"				決済金額が不正な場合
			 *	status_code:"103",status_string:"INVALID_RATING"			商品レイティングが不正な場合
			 *	status_code:"104",status_string:"INVALID_ORDER_ID"			発注IDに利用できない文字が含まれている場合
			 *	status_code:"105",status_string:"INVALID_TIMEOUT"			決済有効期間が不正な場合
			 *	status_code:"106",status_string:"INVALID_NOTIFY_URL"		完了通知URLが不正な場合
			 *	status_code:"107",status_string:"INVALID_RETURN_URL"		正常時戻りURLが不正な場合
			 *	status_code:"108",status_string:"INVALID_CANCEL_URL"		キャンセルURLが不正な場合
			 *	status_code:"200",status_string:"SHOP_KEY_MISMATCH"			加盟店キーが不一致の場合
			 *	status_code:"201",status_string:"OUT_OF_RANGE_PRICE"		決済金額が有効範囲(0～200,000)外の場合
			 *	status_code:"202",status_string:"RATING_MISMATCH"			ST加盟店が商品レイティングにEXを指定した場合
			 *	status_code:"203",status_string:"TOO_LONG_ORDER_ID"			発注IDの文字列長が制限(32文字)を超えている場合
			 *	status_code:"204",status_string:"OUT_OF_RANGE_TIMEOUT"		決済有効期間が有効範囲(0～86400)を超えている場合
			 *	status_code:"300",status_string:"ACCESS_DENIED"				許可されていないアクセス元IPアドレスの場合
			 *	status_code:"301",status_string:"SETTLE_TYPE_MISMATCH"		決済種別が異なる場合(API型決済の加盟店の場合)
			 *	status_code:"302",status_string:"SHOP_CLOSED"				加盟店が開店状態ではない場合
			 *	status_code:"900",status_string:"SYSTEM_ERROR"				その他、システムエラー
			 */

			request.setCharacterEncoding("UTF-8");
		    response.setContentType("text/html;charset=UTF-8");
		    PrintWriter out = response.getWriter();
		    try {
			    out.println("<html lang=\"ja\" >");
			    out.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
			    out.println("<head>");
			    out.println("	<title>決済API-javaサンプル | 6.決済エラー画面</title>");
			    out.println("</head>");
			    out.println("<body>");
			    out.println("	<div id=\"content\">");
			    out.println("		<h3>決済API-javaサンプル | 6.決済エラー画面</h3>");
			    out.println("<br />");
			    out.println("			<h3>お手続きに失敗しました</h3>");
			    out.println("<br />");
			    out.println("	</div>");
			    out.println("</body>");
		    } finally {
		    	out.close();
		    }
		}
	}

}
