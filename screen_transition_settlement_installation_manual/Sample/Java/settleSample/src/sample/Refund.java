package sample;


import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 決済取消-javaサンプル
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
@WebServlet("/Refund")
public class Refund extends HttpServlet {
	private static final long serialVersionUID = 1L;
	public Refund() {
		super();
	}

	/**
	 * 決済取消対象トランザクションID入力画面
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");
		PrintWriter out = response.getWriter();
		out.println("<html lang=\"ja\" >");
		out.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
		out.println("<head>");
		out.println("	<title>決済API-javaサンプル | 決済取消対象入力画面</title>");
		out.println("</head>");
		out.println("<body>");
		out.println("	<div id=\"content\">");
		out.println("		<h3>決済API-javaサンプル | 決済取消対象入力画面</h3>");
		out.println("		<form method=\"post\" action=\"refundSettle\">");
		out.println("<br />");
		out.println("			<h3>決済取消対象のトランザクションIDを入力してください</h3>");
		out.println("			<p>トランザクションID<input type=\"text\" name=\"tranId\"></p>");
		out.println("<br />");
		out.println("			<input type=\"submit\" name=\"refund\" value=\"決済取消を行う\" />");
		out.println("<br />");
		out.println("		</form>");
		out.println("	</div>");
		out.println("</body>");
		out.println("</html>");
	}
}