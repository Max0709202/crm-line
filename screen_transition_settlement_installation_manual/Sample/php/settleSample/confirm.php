<?php
/**
 * 支払確認-phpサンプル
 *
 * 支払確認画面のサンプルです。
 * @copyright	Copyright (c) 2014 BitCash Inc. All Rights Reserved.
 * @author		BitCash
 * @since		2014/1/10
 *
 * < 免責事項 >
 * ①サンプルプログラムはBitCash決済を導入するための参考として、弊社が十分に動作確認を行った上で提供しておりますが、
 * 加盟店様のご利用環境によっては利用できない場合があります。
 * ご利用は加盟店様ご自身の責任において行っていただきますようお願いいたします。
 *
 * ②本サンプルプログラムのご利用により発生した、いかなるトラブルや損失・損害等につきましては
 * 一切責任を負わないものとします。
 *
 * ③本サンプルプログラムに不備があった場合でも、弊社は修正およびサポートの義務を負いかねます。
 *
 * ④本サンプルプログラムに関する財産権、所有権、知的財産権、その他一切の権限は、弊社に帰属します。
 */
?>
<html lang="ja" >
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<head>
<title>決済-phpサンプル | 3.支払確認画面</title>
</head>
<body>
	<div id="content">
		<h3>決済-phpサンプル | 3.支払確認画面</h3>
		<?php
		/***
		 * リクエストで受け取った決済種別を元に決済情報を取得（DBから取得など）
		 * ※サンプルではハードコーディングをしております。（BitCash以外の決済方法に関しては前画面にリダイレクトします）
		 */
		switch ($_POST['methodKind']) {
			case 1:
				// 決済方法：BitCashの場合
				$action = './settle.php';
				$methodName = 'BitCash';
				break;
			default:
				// 決済方法：BitCash以外の場合
				header("Location: ./selectMethod.php");
				break;
				exit;
		}
		?>
		<form method="post" action=<?php print($action); ?> >
			<br />
			<h3>お支払内容を確認してください</h3>
			<br />
			<p>商品名：<?php print($_POST['itemName']); ?></p>
			<p>お支払い金額：<?php print($_POST['itemPrice']); ?></p>
			<p>お支払い方法：<?php print($methodName); ?></p>
			<br />
			<input type="submit" name="next" value="決済" />
			<input type="hidden" name="itemCode" value="<?php print($_POST['itemCode']); ?>" />
			<input type="hidden" name="itemName" value="<?php print($_POST['itemName']); ?>" />
			<input type="hidden" name="itemRating" value="<?php print($_POST['itemRating']); ?>" />
			<input type="hidden" name="itemPrice" value="<?php print($_POST['itemPrice']); ?>" />
			<br />
		</form>
	</div>
</body>
</html>