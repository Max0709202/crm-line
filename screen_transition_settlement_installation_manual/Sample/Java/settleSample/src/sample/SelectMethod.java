package sample;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
@WebServlet("/SelectMethod")
public class SelectMethod extends HttpServlet {
	private static final long serialVersionUID = 1L;

    public SelectMethod() {
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
	    int itemCode = Integer.parseInt(request.getParameter("itemCode"));

	    /***
	     * リクエストで受け取ったアイテムコードを元にアイテム情報を取得（DBから取得など）
	     * ※サンプルではハードコーディングをしております。
	     */
	    String itemName = null;
	    String itemRating = null;
	    String itemPrice = null;
	    switch (itemCode) {
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
			response.setContentType("text/html;charset=UTF-8");
			response.sendRedirect("selectItem");
			break;
		}

	    response.setContentType("text/html;charset=UTF-8");
	    PrintWriter out = response.getWriter();
	    try {
		    out.println("<html lang=\"ja\" >");
		    out.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
		    out.println("<head>");
		    out.println("	<title>決済API-javaサンプル | 2.支払い方法選択画面</title>");
		    out.println("</head>");
		    out.println("<body>");
		    out.println("	<div id=\"content\">");
		    out.println("		<h3>決済API-javaサンプル | 2.支払い方法選択画面</h3>");
		    out.println("		<form method=\"post\" action=\"confirm\">");
		    out.println("<br />");
		    out.println("			<h3>お支払い方法を選択してください</h3>");
		    out.println("<br />");

		    /***
		     * 選択できる決済方法を出力（DBから取得して一覧表示など）
		     * ※サンプルではハードコーディングをしております。
		     */
		    out.println("			<p><input type=\"radio\" name=\"methodKind\" value=\"1\" checked=\"checked\">BitCash</p>");
		    out.println("			<p><input type=\"radio\" name=\"methodKind\" value=\"2\">クレジットカード</p>");


		    out.println("<br />");
		    out.println("			<input type=\"submit\" name=\"next\" value=\"次へ\" />");
		    out.println("			<input type=\"hidden\" name=\"itemCode\" value=\"" + itemCode + "\" />");
		    out.println("			<input type=\"hidden\" name=\"itemName\" value=\"" + itemName + "\" />");
		    out.println("			<input type=\"hidden\" name=\"itemRating\" value=\"" + itemRating + "\" />");
		    out.println("			<input type=\"hidden\" name=\"itemPrice\" value=\"" + itemPrice + "\" />");
		    out.println("<br />");
		    out.println("		</form>");
		    out.println("	</div>");
		    out.println("</body>");
	    } finally {
			out.close();
		}
	}

}
