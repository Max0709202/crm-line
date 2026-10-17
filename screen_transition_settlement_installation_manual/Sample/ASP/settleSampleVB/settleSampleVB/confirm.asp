<%@ LANGUAGE="VBScript" %>
<html>
<head>
<title>決済API-javaサンプル | 3.支払い確認画面</title>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<body>
	<div id="content">
		<h3>決済API-javaサンプル | 3.支払い確認画面</h3>
		<form method="post" action="settle.asp">
<br />
			<h3>支払い内容を確認してください</h3>
<br />

<%

            ' サンプルコード
            ' ダミーの支払い確認画面
            '
            ' @copyright	Copyright (c) 2014 BitCash Inc. All Rights Reserved.
            ' @author		BitCash
            ' @since		2014/1/10
            '
            ' < 免責事項 >
            ' ①サンプルプログラムはBitCash決済を導入するための参考として、弊社が十分に動作確認を行った上で提供しておりますが、
            ' 加盟店様のご利用環境に よっては利用できない場合があります。
            ' ご利用は加盟店様ご自身の責任において行っていただきますようお願いいたします。
            '
            ' ②本サンプルプログラムのご利用により発生した、いかなるトラブルや損失・損害等につきましては
            ' 一切責任を負わないものとします。
            '
            ' ③本サンプルプログラムに不備があった場合でも、弊社は修正およびサポートの義務を負いかねます。
            '
            ' ④本サンプルプログラムに関する財産権、所有権、知的財産権、その他一切の権限は、弊社に帰属します。

		    '決済種別を元に決済情報を取得（DBから取得など）
		    '※サンプルではハードコーディングをしております。

            methodKind = Request.Form("methodKind")
            itemName = Request.Form("itemName")
            itemRating = Request.Form("itemRating")
            itemPrice = Request.Form("itemPrice")

            select case methodKind
                case 1
			        '決済種別：BitCashの場合
			        action = "Settle"
			        methodName = "BitCash"                
            end select
%>

			<p>商品名：<% = itemName %></p>
			<p>お支払い金額：<% = FormatNumber(itemPrice, 0, -1, 0, -1) %></p>
			<p>お支払い方法：<% = methodName %></p>
<br />
			<input type="submit" name="next" value="決済" />
			<input type="hidden" name="itemRating" value="<% = itemRating %>" />
			<input type="hidden" name="itemPrice" value="<% = itemPrice %>" />
<br />
		</form>
	</div>
</body>
</html>