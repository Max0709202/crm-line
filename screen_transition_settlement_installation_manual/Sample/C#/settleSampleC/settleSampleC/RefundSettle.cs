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
     * 決済取消-サンプル
     * 取消対象のトランザクションID（決済時に払い出される、ユーザ様のお支払情報を一意に特定するID）を
     * 指定することで、エンドユーザが決済に利用したビットキャッシュに支払金額分の残高を戻します。
     * 当サンプルでは、前画面より入力されたトランザクションIDの決済取消処理を行います。
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
    public class RefundSettle
    {
        /***
         * 決済確認処理引数として確認対象トランザクションID（決済処理時に払い出される、ユーザ様のお支払情報を一意に特定するIDです。）をご指定下さい
         */
        public Hashtable Refund(String tranID)
        {
            /***
             *  加盟店様の認証に使用する情報
             ***/
            String shopId = WebConfigurationManager.AppSettings["shop_id"]; 			// 契約時にお渡しした加盟店ID
            String shopKey = WebConfigurationManager.AppSettings["shop_key"];		    // 契約時にお渡しした加盟店キー

            // URLエンコードで使用するエンコードタイプ
            Encoding httpEncode = Encoding.UTF8;
            // ファイル出力で使用するエンコードタイプ
            Encoding fileEncode = Encoding.UTF8;

            // POSTするパラメータの文字列作成
            StringBuilder paramBuilder = new StringBuilder();
            paramBuilder
                .Append("shop_id=").Append(shopId) 			// 契約時にお渡しした加盟店ID
                .Append("&shop_key=").Append(shopKey)		// 契約時にお渡しした加盟店キー
                .Append("&tran_id=").Append(tranID);		// トランザクションID


        
            byte[] postData = httpEncode.GetBytes(paramBuilder.ToString());

            /* ビットキャッシュ-決済取消APIのURL */
            String confirmSettleUrl = WebConfigurationManager.AppSettings["refund_settle_url"];
            HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(
                confirmSettleUrl
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
            String receiveFilePath = WebConfigurationManager.AppSettings["refund_param_file_path"];		// 受信パラメータ出力ファイルパス
            String receiveFileSep = ",";				// 区切り文字

            StreamWriter receiveFileStreamWriter = null;
            try
            {
                if( !File.Exists(receiveFilePath) ) {
                    receiveFileStreamWriter = new StreamWriter(receiveFilePath, false, fileEncode);
            
                    // ファイルが存在しなかった場合、ヘッダ出力
                    StringBuilder headerBuilder = new StringBuilder();
                    headerBuilder
					    .Append("status_code").Append(receiveFileSep)
					    .Append("status_string").Append(receiveFileSep)
					    .Append("settle_id").Append(receiveFileSep)
                        .Append("settle_date").Append(receiveFileSep)
                        .Append("refund_id").Append(receiveFileSep)
                        .Append("refund_date").Append(receiveFileSep)
					    .Append("cno");
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
                    .Append(paramMap["settle_id"]).Append(receiveFileSep)
                    .Append(paramMap["settle_date"]).Append(receiveFileSep)
                    .Append(paramMap["refund_id"]).Append(receiveFileSep)
                    .Append(paramMap["refund_date"]).Append(receiveFileSep)
                    .Append(paramMap["cno"]);

                    receiveFileStreamWriter.WriteLine(dataBuilder.ToString());                
            } finally {
                if (receiveFileStreamWriter != null)
                {
                    receiveFileStreamWriter.Close();
                }
            }

            if (((String)paramMap["status_string"]).Equals("SUCCESS"))
            {
                /* 決済取消処理が成功した場合
		         * 決済取消処理結果（ステータスコード、ステータス文字列の詳細については導入マニュアルをご参照ください。）
		         *		status_code				処理結果を示すステータスコード
		         *		status_string			処理結果を示すステータス文字列
		         *		settle_id				決済コード
		         *		settle_date				決済日時（yyyy/mm/dd h24:mi:ss 形式）
		         *		refund_id				リファンドコード
		         *		refund_date				リファンド日時（yyyy/mm/dd h24:mi:ss 形式）
		         *		cno						管理番号
                 */

                /***
                 * 加盟店様側処理
                 * ※確認結果に応じて、決済情報取得成功時に行う加盟店様側処理をご記述ください。
                 */
            }
            else
            {
                /*	決済確認処理が失敗した場合
                 *	status_code:"100",status_string:"INVALID_SHOP_ID"			加盟店IDが不正な場合
                 *	status_code:"101",status_string:"INVALID_SHOP_KEY"			加盟店キーが不正な場合
                 *	status_code:"102",status_string:"INVALID_PRICE"				決済金額が不正な場合
                 *	status_code:"200",status_string:"SHOP_KEY_MISMATCH"			加盟店キーが不一致の場合
                 *	status_code:"300",status_string:"ACCESS_DENIED"				許可されていないアクセス元IPアドレスの場合
                 *	status_code:"301",status_string:"SETTLE_TYPE_MISMATCH"		決済種別が異なる場合(API型決済の加盟店の場合)
                 *	status_code:"302",status_string:"SHOP_CLOSED"				加盟店が開店状態ではない場合
                 *	status_code:"400",status_string:"NOT_SETTLED"				決済されていない場合
                 *	status_code:"401",status_string:"PIN_STOPPED"				決済に利用したビットキャッシュが停止されている場合
                 *	status_code:"402",status_string:"ALREADY_REFUNDED"			既に決済取消済みの場合  
                 *	status_code:"900",status_string:"SYSTEM_ERROR"				その他、システムエラー
                 */
            }
            return paramMap;
           

        }
    }


}