using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using System.Web;
using System.Web.Configuration;

namespace settleSampleC
{

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
    public class Settle
    {

        public String PrepareSettle(String itemPrice, String itemRating)
        {
            /***
             *  加盟店様の認証に使用する情報
             ***/
            String shopId = WebConfigurationManager.AppSettings["shop_id"]; 			// 契約時にお渡しした加盟店ID
            String shopKey = WebConfigurationManager.AppSettings["shop_key"];		    // 契約時にお渡しした加盟店キー

            // ビットキャッシュ決済画面の表示有効期限の設定(秒単位で設定。サンプルは10分)。任意の期限を設定してください
            String timeOut = WebConfigurationManager.AppSettings["timeout"];

            // 決済完了通知URL ユーザ様がビットキャッシュ決済画面にてお支払が完了した際、ビットキャッシュより通知を行うURLです
            // 定期実行のみでビットキャッシュからのお支払完了通知が不要の場合は、「none」を指定してください
            String notifyUrl = WebConfigurationManager.AppSettings["notify_url"];

            // お支払完了後、加盟店様サイトへ戻る際のURLです
            String returnUrl = WebConfigurationManager.AppSettings["return_url"];

            // ユーザ様がビットキャッシュでのお支払の前に加盟店様サイトへ戻る際のURLです
            // サンプルでは、商品選択画面を設定しています
            String cancelUrl = WebConfigurationManager.AppSettings["cancel_url"];

            // オーダーID(注文番号) サンプルのため、現在日時を設定していますが、加盟店様にてユーザ様の支払情報を特定するIDを設定してください
            String orderId = "ORDER_" + DateTime.Now.Ticks.ToString();

            // URLエンコードで使用するエンコードタイプ
            Encoding httpEncode = Encoding.UTF8;
            // ファイル出力で使用するエンコードタイプ
            Encoding fileEncode = Encoding.UTF8;

            // POSTするパラメータの文字列作成
            StringBuilder paramBuilder = new StringBuilder();
            paramBuilder
                .Append("shop_id=").Append(shopId) 			// 契約時にお渡しした加盟店ID
                .Append("&shop_key=").Append(shopKey)		// 契約時にお渡しした加盟店キー
                .Append("&price=").Append(itemPrice)		// お支払金額
                .Append("&rating=").Append(itemRating)		// 商品のレイティング
                .Append("&order_id=").Append(orderId)		// オーダーID(注文番号) サンプルのため、現在日時を設定していますが、加盟店様にてユーザ様の支払情報を特定するIDを設定してください
                .Append("&timeout=").Append(timeOut)
                .Append("&notify_url=").Append(notifyUrl)
                .Append("&return_url=").Append(returnUrl)
                .Append("&cancel_url=").Append(cancelUrl);


            /***
             * 送信パラメータ出力（証跡として出力しておきます）
             */
            String sendFilePath = WebConfigurationManager.AppSettings["start_settle_send_param_file_path"];		// 送信パラメータ出力ファイルパス
            String sendFileSep = ",";			    	// 区切り文字

            StreamWriter sendFileStreamWriter = null;
            try
            {
                if (!File.Exists(sendFilePath))
                {
                    // ファイルが存在しなかった場合、ヘッダ出力
                    sendFileStreamWriter = new StreamWriter(sendFilePath, false, fileEncode);
                    StringBuilder headerBuilder = new StringBuilder();
                    headerBuilder
                        .Append("shop_id").Append(sendFileSep)
                        .Append("shop_key").Append(sendFileSep)
                        .Append("price").Append(sendFileSep)
                        .Append("rating").Append(sendFileSep)
                        .Append("order_id").Append(sendFileSep)
                        .Append("timeout").Append(sendFileSep)
                        .Append("notify_url").Append(sendFileSep)
                        .Append("return_url").Append(sendFileSep)
                        .Append("cancel_url");
                    sendFileStreamWriter.WriteLine(headerBuilder.ToString());
                }
                else
                {
                    sendFileStreamWriter = new StreamWriter(sendFilePath, true, fileEncode);
                }

                // データ出力
                StringBuilder dataBuilder = new StringBuilder();
                dataBuilder
                    .Append(shopId).Append(sendFileSep)
                    .Append(shopKey).Append(sendFileSep)
                    .Append(itemPrice).Append(sendFileSep)
                    .Append(itemRating).Append(sendFileSep)
                    .Append(orderId).Append(sendFileSep)
                    .Append(timeOut).Append(sendFileSep)
                    .Append(notifyUrl).Append(sendFileSep)
                    .Append(returnUrl).Append(sendFileSep)
                    .Append(cancelUrl);

                sendFileStreamWriter.WriteLine(dataBuilder.ToString());
            }
            finally
            {
                if (sendFileStreamWriter != null)
                {
                    sendFileStreamWriter.Close();
                }
            }

            byte[] postData = httpEncode.GetBytes(paramBuilder.ToString());

            /* ビットキャッシュ-決済準備処理用のURL */
            String prepareSettleUrl = WebConfigurationManager.AppSettings["prepare_settle_url"];
            HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(
                prepareSettleUrl
            );
            httpWebRequest.Method = "POST";
            httpWebRequest.ContentType = "application/x-www-form-urlencoded";
            httpWebRequest.ContentLength = paramBuilder.Length;

            /***
             * リクエスト出力
             * ポスト・データの書き込み
             */ 
            Stream requestStream = httpWebRequest.GetRequestStream();
            try
            {
                requestStream.Write(postData, 0, postData.Length);
            }
            finally
            {
                requestStream.Close();
            }

            String responseString = null;
            /***
             * レスポンスの取得と読み込み
             */ 
            WebResponse webResponse = httpWebRequest.GetResponse();
            Stream responseStream = webResponse.GetResponseStream();
            try
            {
                StreamReader streamReader = new StreamReader(responseStream, httpEncode);
                try
                {
                    responseString = streamReader.ReadToEnd();
                }
                finally
                {
                    streamReader.Close();
                }
            }
            finally
            {
                responseStream.Close();
            }

            // レスポンスパラメータ分解
            String[] responses = responseString.Split('&');
            Hashtable paramMap = new Hashtable();

            for (int i = 0; i < responses.Length; i++)
            {
                String[] param = responses[i].Split('=');
                paramMap.Add(param[0], param[1]);
            }

            /***
             * 受信パラメータ出力（証跡として出力しておきます）
             */
            String receiveFilePath = WebConfigurationManager.AppSettings["start_settle_receive_param_file_path"];		// 受信パラメータ出力ファイルパス
            String receiveFileSep = ",";				// 区切り文字

            StreamWriter receiveFileStreamWriter = null;
            try
            {
                if(!File.Exists(receiveFilePath)) {
                    // ファイルが存在しなかった場合、ヘッダ出力
                    receiveFileStreamWriter = new StreamWriter(receiveFilePath, false, fileEncode);
                    StringBuilder headerBuilder = new StringBuilder();
                    headerBuilder
                        .Append("status_code").Append(receiveFileSep)
                        .Append("status_string").Append(receiveFileSep)
                        .Append("tran_id").Append(receiveFileSep)
                        .Append("settle_url");
                    receiveFileStreamWriter.WriteLine(headerBuilder.ToString());
                }
                else
                {
                    receiveFileStreamWriter = new StreamWriter(receiveFilePath, true, fileEncode);
                }
                
                // データ出力
			    StringBuilder dataBuilder = new StringBuilder();
			    dataBuilder
				    .Append(paramMap["status_code"]).Append(receiveFileSep)
				    .Append(paramMap["status_string"]).Append(receiveFileSep)
				    .Append(paramMap["tran_id"]).Append(receiveFileSep)
				    .Append(paramMap["settle_url"]);

                    receiveFileStreamWriter.WriteLine(dataBuilder.ToString());                
            } finally {
                if (receiveFileStreamWriter != null)
                {
                    receiveFileStreamWriter.Close();
                }
            }

            if (((String)paramMap["status_string"]).Equals("SUCCESS"))
            {
                /*	決済準備処理が成功した場合
                 * 	status_code:"0"				ステータスコード：処理成功
                 *	status_string:"SUCCEESS"	ステータス文字列：処理成功
                 *	tran_id						決済を一意に識別できるトランザクションID
                 *	settle_url					決済画面URL(暗号化トランザクションID付加)
                 */
            }
            else
            {
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
            }

            return HttpUtility.UrlDecode((String)paramMap["settle_url"], httpEncode);

        }
    }


}