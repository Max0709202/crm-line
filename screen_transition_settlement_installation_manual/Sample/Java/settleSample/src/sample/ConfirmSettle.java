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
 * 決済確認-javaサンプル
 * 確認対象のトランザクションID（決済時に払い出される、ユーザ様のお支払情報を一意に特定するID）を
 * 指定することで、対象決済が完了しているかどうかの確認を行います。
 * 通常、決済完了通知は決済の完了時にリアルタイムで通知されますが、当機能を定時実行することで
 * 決済通知機能を利用しない場合や通信障害等により正常に完了通知が受領できなかった場合に代替することが可能です。
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
@WebServlet("/ConfirmSettle")
public class ConfirmSettle extends HttpServlet {
	private static final long serialVersionUID = 1L;
	public ConfirmSettle() {
		super();
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException{
		request.setCharacterEncoding("UTF-8");
		/***
		 * プロパティファイルの取得
		 * ※WebContent/WEB-INFの配下にある、bitcash.propertiesという名称のファイルです。
		 */
		Properties properties;
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

		// 決済確認APIのURL
		final String CONFIRM_URL = properties.getProperty("confirm_settle_url");

		// 接続用HttpURLConnectionオブジェクト作成
		URL url = new URL(CONFIRM_URL);
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.setDoOutput(true);
		connection.setUseCaches(false);
		connection.setRequestMethod("POST");

		/***
		 * 店舗ID
		 * ※プロパティのshop_idに、契約時にお渡しした店舗IDをご設定ください。
		 */
		String shopId = properties.getProperty("shop_id");

		/***
		 * 加盟店キー
		 * ※プロパティのshop_keyに、契約時にお渡しした認証キーをご設定ください。
		 */
		String shopKey = properties.getProperty("shop_key");

		/***
		 * 確認対象トランザクションID（決済処理時に払い出される、ユーザ様のお支払情報を一意に特定するIDです。）
		 * ※サンプルのためハードコーディングしておりますが、適宜加盟店様システムDBからの取得等へご変更ください。
		 */
		String tranId = "SAMPLE-000001";

		// パラメータ文字列の設定
		String param =
				"shop_id=" + shopId +
				"&shop_key=" +shopKey +
				"&tran_id=" + tranId;

		// 出力ストリームを作成
		PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
		// 生成したパラメータをPOSTする
		try {
			printWriter.print(param);
		} finally {
			// 出力ストリームをクローズ
			printWriter.close();
		}

		// レスポンスデータ行
		String line;
		// レスポンスデータ
		StringBuilder postResponseStr = new StringBuilder();
		// 入力ストリームを作成してPOSTのレスポンスを取得する
		BufferedReader bufferReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
		// レスポンスを1行ずつ読み込む
		try {
			while ((line = bufferReader.readLine()) != null) {
				// レスポンスデータに追記していく
				postResponseStr
					.append(line);
			}
		} finally {
			// 入力ストリームをクローズ
			bufferReader.close();
		}
		connection.disconnect();

		// レスポンスを"&"で分割し、配列へ格納
		String[] postResponseStrs = postResponseStr.toString().split("&");
		Map<String, String> map = new HashMap<String, String>();
		for ( int paramCount = 0; paramCount<postResponseStrs.length; paramCount ++ ) {
			// "&"で分割したレスポンスをさらに"="で分割する
			String[] postResponseStrsDetail = postResponseStrs[paramCount].toString().split("=");
			// 左辺を配列キー、右辺を値として配列へ格納
			map.put(postResponseStrsDetail[0], postResponseStrsDetail[1]);
		}
		/***
		 *	決済確認結果（ステータスコード、ステータス文字列の詳細については導入マニュアルをご参照ください。）
		 *		status_code				処理結果を示すステータスコード
		 *		status_string			処理結果を示すステータス文字列
		 *		settle_id				決済コード
		 *		price					決済金額
		 *		settle_date				決済日時（yyyy/mm/dd h24:mi:ss 形式）
		 *		refunded				返金フラグ（返金済みの場合は"true"、未返金の場合は"false"）
		 *		cno						管理番号
		 */
		/***
		 * 受信パラメータ出力（証跡として出力）
		 */
		// 定数
		final String RECEIVE_FILE_PATH = properties.getProperty("confirm_param_file_path");	// 受信パラメータ出力ファイルパス
		final String headSep = ",";		// ヘッダ区切り文字
		final String dataSep = ",";		// データ区切り文字
		final String lineSep = "\r\n";	// 行区切り
		final String encode = "UTF-8";	// エンコードタイプ

		File receiveParamFile = new File(RECEIVE_FILE_PATH);
		BufferedWriter receiveParamFileBufferedWriter = null;
		try {
			if( !receiveParamFile.exists() ) {
				// ファイルが存在しない場合
				receiveParamFile.createNewFile();

				receiveParamFileBufferedWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(receiveParamFile, false), encode)
					);

				/***
				 * ヘッダ部出力
				 */
				StringBuilder headerBuilder = new StringBuilder();
				headerBuilder
					.append("status_code").append(headSep)
					.append("status_string").append(headSep)
					.append("settle_id").append(headSep)
					.append("price").append(headSep)
					.append("settle_date").append(headSep)
					.append("refunded").append(headSep)
					.append("cno").append(lineSep);

				// ヘッダ書き込み
				receiveParamFileBufferedWriter.write(headerBuilder.toString());
			} else {
				receiveParamFileBufferedWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(receiveParamFile, true), encode)
					);
			}
			/***
			 * データ部出力
			 */
			StringBuilder dataBuilder = new StringBuilder();
			dataBuilder
				.append(map.get("status_code")).append(dataSep)
				.append(map.get("status_string")).append(dataSep)
				.append(map.get("settle_id")).append(dataSep)
				.append(map.get("price")).append(dataSep)
				.append(map.get("settle_date")).append(dataSep)
				.append(map.get("refunded")).append(dataSep)
				.append(map.get("cno")).append(lineSep);

			// データ書き込み
			receiveParamFileBufferedWriter.write(dataBuilder.toString());
		} finally {
			if( receiveParamFileBufferedWriter != null ) {
				receiveParamFileBufferedWriter.close();
			}
		}

		if ( map.get("status_string").equals("SUCCESS") ) {
			//	正常に取得成功した場合

			/***
			 * 加盟店様側処理
			 * ※確認結果に応じて、決済情報取得成功時に行う加盟店様側処理をご記述ください。
			 */


		}
	}
}
