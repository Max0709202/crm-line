using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Web;
using System.Web.Configuration;
using System.Web.UI;
using System.Web.UI.WebControls;

namespace settleSampleC
{
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
    public partial class AcceptNotification : System.Web.UI.Page
    {
        protected void Page_Load(object sender, EventArgs e)
        {
            if (!IsPostBack)
            {

                /*	ビットキャッシュから送信されるパラメータ（エンコードされているため、使用時はデコードが必要）
                 *	tran_id			決済を一意に識別できるトランザクションID
                 *	order_id		決済開始処理にて加盟店様から送信された発注コード
                 *	settle_date		決済日時（yyyy/mm/dd h24:mi:ss形式）
                 *	price			決済金額
                 *	hash			パラメータ改ざん検知のためのハッシュ値
                 */
                String tranId = Request["tran_id"];
                String orderId = Request["order_id"];
                String settleDate = Request["settle_date"];
                String price = Request["price"];
                String hash = Request["hash"];

                // URLエンコードで使用するエンコードタイプ
                Encoding httpEncode = Encoding.UTF8;
                // ファイル出力で使用するエンコードタイプ
                Encoding fileEncode = Encoding.UTF8;

                /***
		         * 受信パラメータ出力
		         */
		        // 定数
	            String receiveFilePath = WebConfigurationManager.AppSettings["accept_notification_receive_param_file_path"];		// 受信パラメータ出力ファイルパス
                String receiveFileSep = ",";				// 区切り文字

                StreamWriter receiveFileStreamWriter = null;
                try
                {
                    if( !File.Exists(receiveFilePath) ) {
                        // ファイルが存在しなかった場合、ヘッダ出力
                        receiveFileStreamWriter = new StreamWriter(receiveFilePath, false, fileEncode);
                        StringBuilder headerBuilder = new StringBuilder();
                        headerBuilder
                            .Append("tran_id").Append(receiveFileSep)
                            .Append("order_id").Append(receiveFileSep)
                            .Append("settle_date").Append(receiveFileSep)
                            .Append("price").Append(receiveFileSep)
                            .Append("hash");
                        receiveFileStreamWriter.WriteLine(headerBuilder.ToString());
                    }
                    else
                    {
                        receiveFileStreamWriter = new StreamWriter(receiveFilePath, true, fileEncode);
                    }
                
                    // データ出力
			        StringBuilder dataBuilder = new StringBuilder();
			        dataBuilder
				        .Append(tranId).Append(receiveFileSep)
				        .Append(orderId).Append(receiveFileSep)
				        .Append(settleDate).Append(receiveFileSep)
                        .Append(price).Append(receiveFileSep)
                        .Append(hash);

                        receiveFileStreamWriter.WriteLine(dataBuilder.ToString());                
                } finally {
                    if (receiveFileStreamWriter != null)
                    {
                        receiveFileStreamWriter.Close();
                    }
                }

	            /***
	             * 改ざんチェック
	             * 悪意のある第三者が不正なパラメータを設定しリクエストを送信するケースがあるため
	             * ハッシュ値を用いてチェックを行い、不正な呼び出しや改ざんを防止します。
	             */

		        /* 導入キットに含まれている通知キー */
                String notifyKey = WebConfigurationManager.AppSettings["notify_key"];

		        /*
		         * ハッシュ値生成用文字列
		         * tran_id=xxx&order_settle_date=price=xxx加盟店通知キー
		         */
	            StringBuilder hashSourceBuilder = new StringBuilder();
	            hashSourceBuilder
                    .Append("tran_id=").Append(UrlEncode(tranId, httpEncode))
                    .Append("&order_id=").Append(UrlEncode(orderId, httpEncode))
                    .Append("&settle_date=").Append(UrlEncode(settleDate, httpEncode))
                    .Append("&price=").Append(UrlEncode(price, httpEncode))
                    .Append(notifyKey);

	            /***
	             * ハッシュ値生成
	             */

                //文字列をbyte型配列に変換する
                byte[] data = httpEncode.GetBytes(hashSourceBuilder.ToString());
                byte[] hashByte;

                //SHA1CryptoServiceProviderオブジェクトを作成
                SHA1CryptoServiceProvider sha1 = new SHA1CryptoServiceProvider();
                try
                {
                    //ハッシュ値を計算する
                    hashByte = sha1.ComputeHash(data);
                }
                finally
                {
                    sha1.Clear();
                }

                // byte型配列を16進数の文字列に変換
                StringBuilder hexHash = new StringBuilder();
                foreach (byte b in hashByte)
                {
                    hexHash.Append(b.ToString("x2"));
                }


                if ( hexHash.ToString().CompareTo(hash) != 0 )
                {
                    /***
                     * 改ざん検知時
                     * 加盟店様側でエラー時処理を行って下さい
                     */ 
			        throw new Exception("改ざんされていました");
                }
                else
                {
                    /***
                     * 加盟店様側処理
                     * トランザクションID、オーダーIDを元に購入者および購入情報を特定し
                     * 商品の受け渡しを行って、支払処理を完了させて下さい。
                     */
                }

		        /***
		         * ビットキャッシュへの応答（HTTPレスポンスコード200）
		         */
                Response.StatusCode = 200;
            }
        }


        /***
         * ビットキャッシュ側のPOSTパラメータはjava.net.URLEncoderによりURLエンコードが行われておりますが
         * C#のHttpUtility.UrlEncodeとjava.net.URLEncoderによるURLエンコードでは挙動に差異があるためハッシュ値生成に影響がでます。
         * （HttpUtility.UrlEncodeの場合%xx(xxは16進数)で使うアルファベットが小文字）
         * そのため以下のURLエンコードクラスを用います
         */
        protected string unreservedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~";
 
        protected string UrlEncode(string value, Encoding encode)
        {
            StringBuilder result = new StringBuilder();
            byte[] data = encode.GetBytes(value);

            int len = data.Length;

            for (int i = 0; i < len; i++)
            {
                int c = data[i];
                if (c == ' ')
                {
                    result.Append('+');
                } 
                else if (c < 0x80 && unreservedChars.IndexOf((char)c) != -1)
                {
                    result.Append((char)c);
                }
                else
                {
                    result.Append('%' + String.Format("{0:X2}", (int)data[i]));
                }

            }
            return result.ToString();
        }

    }
}