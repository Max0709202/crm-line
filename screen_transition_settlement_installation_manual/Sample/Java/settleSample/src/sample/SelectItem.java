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
 * ダミーの購入商品選択画面
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
@WebServlet("/SelectItem")
public class SelectItem extends HttpServlet {
	private static final long serialVersionUID = 1L;

    public SelectItem() {
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub

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

		// HTML出力
	    response.setContentType("text/html;charset=UTF-8");
	    PrintWriter out = response.getWriter();
	    try {
		    out.println("<html lang=\"ja\" >");
		    out.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
		    out.println("<head>");
		    out.println("	<title>決済API-javaサンプル | 1.購入商品選択画面</title>");
		    out.println("</head>");
		    out.println("<body>");
		    out.println("	<div id=\"content\">");
		    out.println("		<h3>決済API-javaサンプル | 1.購入商品選択画面</h3>");
		    out.println("		<form method=\"post\" action=\"selectMethod\">");
		    out.println("<br />");
		    out.println("			<h3>購入する商品を選択してください</h3>");
		    out.println("<br />");

		    /***
		     * 選択できる商品を出力（DBから取得して一覧表示など）
		     * ※サンプルではハードコーディングをしております。
		     */
		    out.println("			<p><input type=\"radio\" name=\"itemCode\" value=\"1\" checked=\"checked\">商品A:1,000円（全年齢）</p>");
		    out.println("			<p><input type=\"radio\" name=\"itemCode\" value=\"2\">商品B:3,000円（全年齢）</p>");
		    out.println("			<p><input type=\"radio\" name=\"itemCode\" value=\"3\">商品C:5,000円（全年齢）</p>");
		    out.println("			<p><input type=\"radio\" name=\"itemCode\" value=\"4\">商品D:1,000円（20歳以上）</p>");
		    out.println("			<p><input type=\"radio\" name=\"itemCode\" value=\"5\">商品E:3,000円（20歳以上）</p>");
		    out.println("			<p><input type=\"radio\" name=\"itemCode\" value=\"6\">商品F:5,000円（20歳以上）</p>");



		    out.println("<br />");
		    out.println("			<input type=\"submit\" name=\"next\" value=\"次へ\" />");
		    out.println("<br />");
		    out.println("		</form>");
		    out.println("	</div>");
		    out.println("</body>");
		    out.println("</html>");
	    } finally {
			out.close();
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

	}

}
