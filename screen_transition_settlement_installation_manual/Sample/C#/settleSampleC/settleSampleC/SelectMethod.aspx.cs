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
     * ダミーの支払い方法選択画面
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
    public partial class SelectMethod : System.Web.UI.Page
    {
        protected void Page_Load(object sender, EventArgs e)
        {
            if (!IsPostBack)
            {
                if (Request["itemCode"] != null)
                {

                    int itemCode = int.Parse(Request["itemCode"]);

                    /***
                        * リクエストで受け取ったアイテムコードを元にアイテム情報を取得（DBから取得など）
                        * ※サンプルではハードコーディングをしております。
                        */

                    String itemName = null;
                    String itemRating = null;
                    String itemPrice = null;
                    switch (itemCode)
                    {
                        case 1:
                            itemName = "商品A";
                            itemRating = "19";
                            itemPrice = "1000";
                            break;
                        case 2:
                            itemName = "商品B";
                            itemRating = "19";
                            itemPrice = "3000";
                            break;
                        case 3:
                            itemName = "商品C";
                            itemRating = "19";
                            itemPrice = "5000";
                            break;
                        case 4:
                            itemName = "商品D";
                            itemRating = "99";
                            itemPrice = "1000";
                            break;
                        case 5:
                            itemName = "商品E";
                            itemRating = "99";
                            itemPrice = "3000";
                            break;
                        case 6:
                            itemName = "商品F";
                            itemRating = "99";
                            itemPrice = "5000";
                            break;
                        default:
                            // 存在しないアイテムコードを選択された場合
                            // 前画面(商品選択画面)へリダイレクトします
                            Response.Redirect("~/SelectItem.aspx");
                            break;
                    }

                    HiddenItemCode.Value = itemCode.ToString();
                    HiddenItemName.Value = itemName;
                    HiddenItemRating.Value = itemRating;
                    HiddenItemPrice.Value = itemPrice;
                }
                else
                {
                    // 前画面からのパラメータが存在しなかった場合
                    Response.Redirect("~/SelectItem.aspx");
                }
                
            }
            
        }

        protected void ButtonSelectMethod_Click(object sender, EventArgs e)
        {

            StringBuilder paramBuilder = new StringBuilder();
            paramBuilder
                .Append("?itemCode=").Append(Request["HiddenItemCode"])
                .Append("&itemName=").Append(Request["HiddenItemName"])
                .Append("&itemRating=").Append(Request["HiddenItemRating"])
                .Append("&itemPrice=").Append(Request["HiddenItemPrice"])
                .Append("&MethodKind=").Append(Request["MethodKind"])
                ;


            Response.Redirect("~/Confirm.aspx" + paramBuilder.ToString());
        }
    }
}