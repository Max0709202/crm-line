using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using System.Web.UI;
using System.Web.UI.WebControls;

namespace settleSampleC
{

    /**
     * サンプルコード
     * ダミーの支払い確認画面
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
    public partial class Confirm : System.Web.UI.Page
    {
        protected void Page_Load(object sender, EventArgs e)
        {
            if (!IsPostBack)
            {

                if (Request["MethodKind"] != null)
                {

                    int methodKind = int.Parse(Request["MethodKind"]);

                    String itemName = Request["itemName"];
                    String itemPrice = Request["itemPrice"];
                    String itemRating = Request["itemRating"];
                    int castItemPrice = int.Parse(itemPrice);

                    /***
                     * 決済種別を元に決済情報を取得（DBから取得など）
                     * ※サンプルはハードコーディングをしております。（BitCash以外の決済方法に関しては前画面リダイレクトします）
                     */
                        
                    String methodName = null;
                    switch (methodKind)
                    {
                        case 1:
                            // 決済種別：BitCashの場合
                            methodName = "BitCash";
                            break;
                        default:
                            // 存在しない決済種別を選択された場合
                            // 前画面(決済種別選択画面)へリダイレクトします
                            Response.Redirect("~/SelectMethod.aspx");
                            break;
                    }

                    LabelItemName.Text = itemName;
                    LabelItemPrice.Text = castItemPrice.ToString("#,0");
                    LabelMethodName.Text = methodName;
                    this.HiddenItemPrice.Value = itemPrice;
                    this.HiddenItemRating.Value = itemRating;

                }
                else
                {
                    // 前画面からのパラメータが存在しなかった場合
                    Response.Redirect("~/SelectItem.aspx");
                }
                
            }
        }

        protected void ButtonConfirm_Click(object sender, EventArgs e)
        {
            // 決済開始処理クラス生成
            Settle settle = new Settle();
            String settleUrl = settle.PrepareSettle(this.HiddenItemPrice.Value, this.HiddenItemRating.Value);

            if (settleUrl.CompareTo("NA") != 0)
            {
                // 決済URLの戻り値が存在した場合、決済URLにリダイレクト
                Response.Redirect(settleUrl);
            }
            else
            {
                // 決済URLの戻り値が存在しなかった場合、エラー処理
            }
            
        }
    }
}