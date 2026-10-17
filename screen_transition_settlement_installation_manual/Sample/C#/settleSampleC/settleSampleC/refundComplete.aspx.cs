using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Web;
using System.Web.UI;
using System.Web.UI.WebControls;

namespace settleSampleC
{
    /**
     * サンプルコード
     * ダミーの決済完了画面
     *
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
    public partial class RefundComplete : System.Web.UI.Page
    {
        protected void Page_Load(object sender, EventArgs e)
        {
            if (!IsPostBack)
            {
                this.LabelStatus.Text = Request["status_string"];
                this.LabelSettleId.Text = Request["settle_id"];
                this.LabelSettleDate.Text = Request["settle_id"];
                this.LabelRefundId.Text = Request["refund_id"];
                this.LabelRefundDate.Text = Request["refund_date"];
                this.LabelCno.Text = Request["cno"];
            }
        }

    }
}