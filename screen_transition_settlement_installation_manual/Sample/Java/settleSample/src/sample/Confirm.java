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
@WebServlet("/Confirm")
public class Confirm extends HttpServlet {
	private static final long serialVersionUID = 1L;

    public Confirm() {
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
	    String itemName = request.getParameter("itemName");
	    String itemRating = request.getParameter("itemRating");
	    String itemPrice = request.getParameter("itemPrice");
	    int parseItemPrice = Integer.parseInt(itemPrice);
	    int methodKind = Integer.parseInt(request.getParameter("methodKind"));

	    /***
	     * 決済種別を元に決済情報を取得（DBから取得など）
	     * ※サンプルはハードコーディングをしております。（BitCash以外の決済方法に関しては前画面リダイレクトします）
	     */
	    String action = null;
	    String methodName = null;
	    switch (methodKind) {
		case 1:
			// 決済種別：BitCashの場合
			action = "Settle";
			methodName = "BitCash";
		    break;
		default:
			// 上記以外の場合
			// 前画面(決済種別選択画面)へリダイレクトします
			response.setContentType("text/html;charset=UTF-8");
			response.sendRedirect("selectMethod");

			break;
		}
	    response.setContentType("text/html;charset=UTF-8");
	    PrintWriter out = response.getWriter();
	    try {
		    out.println("<html lang=\"ja\" >");
		    out.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
		    out.println("<head>");
		    out.println("	<title>決済API-javaサンプル | 3.支払い確認画面</title>");
		    out.println("</head>");
		    out.println("<body>");
		    out.println("	<div id=\"content\">");
		    out.println("		<h3>決済API-javaサンプル | 3.支払い確認画面</h3>");
		    out.println("		<form method=\"post\" action=\"" + action + "\">");
		    out.println("<br />");
		    out.println("			<h3>支払い内容を確認してください</h3>");
		    out.println("<br />");
		    out.println("			<p>商品名：" + itemName + "</p>");
		    out.println("			<p>お支払い金額：" + String.format("%1$,3d", parseItemPrice) + "</p>");
		    out.println("			<p>お支払い方法：" + methodName + "</p>");
		    out.println("<br />");
		    out.println("			<input type=\"submit\" name=\"next\" value=\"決済\" />");
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
