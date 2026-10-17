using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Web;
using System.Web.UI;
using System.Web.UI.WebControls;

namespace settleSampleC
{
    /**
     * サンプル
     * ダミーの決済取消画面
     * 取消対象のトランザクションID（決済時に払い出される、ユーザ様のお支払情報を一意に特定するID）を
     * 指定することで、エンドユーザが決済に利用したビットキャッシュに支払金額分の残高を戻します。
     * 当サンプルでは、画面より対象トランザクションIDを入力して指定しています。
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

    public partial class Refund : System.Web.UI.Page
    {
        protected void Page_Load(object sender, EventArgs e)
        {

        }


        protected void ButtonRefund_Click(object sender, EventArgs e)
        {
            RefundSettle refundSettle = new RefundSettle();
            Hashtable refundHash = refundSettle.Refund(this.TextBoxTranId.Text);

            StringBuilder paramBuilder = new StringBuilder();
            paramBuilder
                .Append("?status_string=").Append(refundHash["status_string"])
                .Append("&settle_id=").Append(refundHash["settle_id"])
                .Append("&settle_date=").Append(refundHash["settle_date"])
                .Append("&refund_id=").Append(refundHash["refund_id"])
                .Append("&refund_date=").Append(refundHash["refund_date"])
                .Append("&cno=").Append(refundHash["cno"])
                ;


            Response.Redirect("~/RefundComplete.aspx" + paramBuilder.ToString());
        }
    }
}