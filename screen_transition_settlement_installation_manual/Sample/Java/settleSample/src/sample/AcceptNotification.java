package sample;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * サンプルコード
 * 完了通知受領
 *
 * 画面遷移型BitCash決済サービスでは、
 * BitCashのひらがなID入力画面等の決済画面はBitCash決済システムがエンドユーザに対して出力（表示）します。
 * エンドユーザがひらがなIDを入力、またはマイビットキャッシュにログインする事で決済処理（BitCashの残高を減算）が行われ、
 * 決済処理完了直後に加盟店様の指定のURLを呼び出し決済完了情報を通知します。
 * BitCash決済システムから加盟店様指定のURLへの通知はHTTP(S) POSTメソッドを利用して行います。
 * 加盟店様側指定のURLで通知を受領できた場合はHTTPレスポンスコードで200（OK）を返却します。
 * HTTPレスポンスコードが200以外の場合は通知を受領できなかったものとして規定時間（約5分間）の間は通知のリトライを行います。
 * 完了通知はBitCash決済システムから加盟店様システム(指定のURL)に対して行われるため、
 * FireWall等で通信を制限している場合はBitCashシステムからの通信を許可するようにしてください。
 * 加盟店様のセキュリティポリシー等により外部からのインバウンド通信を許可できないような場合は、
 * 完了通知を利用せずに「決済確認処理」で代替する事も可能です。
 * ただし、その場合はリアルタイム性が損なわれる事にご注意ください。
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
@WebServlet("/AcceptNotification")
public class AcceptNotification extends HttpServlet {
	private static final long serialVersionUID = 1L;

    public AcceptNotification() {
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

		/*	ビットキャッシュから送信されるパラメータ（エンコードされているため、使用時はデコードが必要）
		 *	tran_id			決済を一意に識別できるトランザクションID
		 *	order_id		決済開始処理にて加盟店様から送信された発注コード
		 *	settle_date		決済日時（yyyy/mm/dd h24:mi:ss形式）
		 *	price			決済金額
		 *	hash			パラメータ改ざん検知のためのハッシュ値
		 */

		request.setCharacterEncoding("UTF-8");
	    String tranId = request.getParameter("tran_id");
	    String orderId = request.getParameter("order_id");
	    String settleDate = request.getParameter("settle_date");
	    String price = request.getParameter("price");
	    String hash = request.getParameter("hash");


		/***
		 * 受信パラメータ出力
		 */
		// 定数
	    final String RECEIVE_FILE_PATH = properties.getProperty("accept_notification_receive_param_file_path");	// 受信パラメータ出力ファイルパス
		final String RECEIVE_FILE_SEP = ",";		// 区切り文字
		final String RECEIVE_FILE_LINE_SEP = "\r\n";		// 行区切り
		final String RECEIVE_FILE_ENCODE = "UTF-8";			// エンコードタイプ

		File outputFile = new File(RECEIVE_FILE_PATH);

		BufferedWriter bufferedWriter = null;

		try {
			if( !outputFile.exists() ) {
				// ファイルが存在しない場合
				outputFile.createNewFile();

				bufferedWriter = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(outputFile, false), RECEIVE_FILE_ENCODE)
				);
				// ヘッダ部出力
				StringBuilder headerBuilder = new StringBuilder();
				headerBuilder
					.append("tran_id").append(RECEIVE_FILE_SEP)
					.append("order_id").append(RECEIVE_FILE_SEP)
					.append("settle_date").append(RECEIVE_FILE_SEP)
					.append("price").append(RECEIVE_FILE_SEP)
					.append("hash")
					.append(RECEIVE_FILE_LINE_SEP);

				// ヘッダ書き込み
				bufferedWriter.write(headerBuilder.toString());

			} else {
				// ファイルが存在する場合
				bufferedWriter = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(outputFile, true), RECEIVE_FILE_ENCODE)
				);
			}

			// データ部出力
			StringBuilder dataBuilder = new StringBuilder();
			dataBuilder
				.append(tranId).append(RECEIVE_FILE_SEP)
				.append(orderId).append(RECEIVE_FILE_SEP)
				.append(settleDate).append(RECEIVE_FILE_SEP)
				.append(price).append(RECEIVE_FILE_SEP)
				.append(hash)
				.append(RECEIVE_FILE_LINE_SEP);

			// データ書き込み
			bufferedWriter.write(dataBuilder.toString());
		} finally {
			if( bufferedWriter != null ) {
			bufferedWriter.close();
			}
		}

	    /***
	     * 改ざんチェック
	     * 悪意のある第三者が不正なパラメータを設定しリクエストを送信するケースがあるため
	     * ハッシュ値を用いてチェックを行い、不正な呼び出しや改ざんを防止します。
	     */

		/* 導入キットに含まれている通知キー */
		final String NOTIFY_KEY = properties.getProperty("notify_key");

		/*
		 * ハッシュ値生成用文字列
		 * tran_id=xxx&order_id&settle_date=xxx&price=xxx加盟店通知キー
		 */
	    StringBuilder hashSourceBuilder = new StringBuilder();
	    hashSourceBuilder
	    	.append("tran_id=").append(URLEncoder.encode(tranId, "UTF-8"))
	    	.append("&order_id=").append(URLEncoder.encode(orderId, "UTF-8"))
	    	.append("&settle_date=").append(URLEncoder.encode(settleDate, "UTF-8"))
	    	.append("&price=").append(URLEncoder.encode(price, "UTF-8"))
	    	.append(NOTIFY_KEY);

	    /***
	     * ハッシュ値生成
	     */
		MessageDigest messageDigest;
		try {
			messageDigest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		messageDigest.update(hashSourceBuilder.toString().getBytes());

		// 16進数に変換
	    byte[] digestValue = messageDigest.digest();
	    StringBuffer hexDigest = new StringBuffer();
	    for (int i = 0; i < digestValue.length; i++) {
	    	int value = digestValue[i] & 0xFF;
	    	if (value < 16) {
	    		hexDigest.append("0");
	    	}
	    	hexDigest.append(Integer.toString(value, 16));
	    }
	    String digest = hexDigest.toString();

		if( digest.compareTo(hash) != 0 ) {
			throw new RuntimeException("改ざんされていました");
		}


		/***
		 * 加盟店様側処理
		 * トランザクションID、オーダーIDを元に購入者および購入情報を特定し
		 * 商品の受け渡しを行って、支払処理を完了させて下さい。
		 */




		/***
		 * ビットキャッシュへの応答（HTTPレスポンスコード200）
		 */
	    response.setContentType("text/html;charset=UTF-8");
	    response.setStatus(HttpServletResponse.SC_OK);
	}

}

