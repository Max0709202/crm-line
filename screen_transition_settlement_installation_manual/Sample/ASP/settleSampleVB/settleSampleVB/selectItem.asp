<%@ LANGUAGE="VBScript" %>
<html>
<head>
<title>決済API-javaサンプル | 1.購入商品選択画面</title>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<body>
	<div id="content">
		<h3>決済API-javaサンプル | 1.購入商品選択画面</h3>
		<form method="post" action="selectMethod.asp">
<br />
			<h3>購入する商品を選択してください</h3>
<br />

<%

            ' サンプルコード
            ' ダミーの購入商品選択画面
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

		    '選択できる商品を出力（DBから取得して一覧表示など）
		    '※サンプルではハードコーディングをしております。
%>

			<p><input type="radio" name="itemCode" value="1" checked="checked">商品A:1,000円（全年齢）</p>
			<p><input type="radio" name="itemCode" value="2">商品B:3,000円（全年齢）</p>
			<p><input type="radio" name="itemCode" value="3">商品C:5,000円（全年齢）</p>
			<p><input type="radio" name="itemCode" value="4">商品D:1,000円（20歳以上）</p>
			<p><input type="radio" name="itemCode" value="5">商品E:3,000円（20歳以上）</p>
			<p><input type="radio" name="itemCode" value="6">商品F:5,000円（20歳以上）</p>



<br />
			<input type="submit" name="next" value="次へ" />
<br />
		</form>
	</div>
</body>
</html>