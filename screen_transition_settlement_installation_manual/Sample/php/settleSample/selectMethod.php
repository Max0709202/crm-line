<?php
/**
 * 購入方法選択-phpサンプル
 *
 * 購入方法選択画面のサンプルです。
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
<title>決済-phpサンプル | 2.支払方法選択画面</title>
</head>
<body>
	<div id="content">
		<h3>決済-phpサンプル | 2.支払方法選択画面</h3>
		<form method="post" action="./confirm.php">
			<br />
			<h3>お支払方法を選択してください</h3>
			<br />

			<?php
			/***
			 * リクエストで受け取ったアイテムコードを基にアイテムの情報を取得（DBから取得など）
			 * ※サンプルではハードコーディングをしております。
			 */
			switch ($_POST['itemCode']) {
				case 1:
					$itemName = '商品A';
					$itemRating = '19';
					$itemPrice = '1000';
					break;
				case 2:
					$itemName = '商品B';
					$itemRating = '19';
					$itemPrice = '3000';
					break;
				case 3:
					$itemName = '商品C';
					$itemRating = '19';
					$itemPrice = '5000';
					break;
				case 4:
					$itemName = '商品D';
					$itemRating = '99';
					$itemPrice = '1000';
					break;
				case 5:
					$itemName = '商品E';
					$itemRating = '99';
					$itemPrice = '3000';
					break;
				case 6:
					$itemName = '商品F';
					$itemRating = '99';
					$itemPrice = '5000';
					break;
				default:
					header("Location: ./selectItem.php");
					break;
					exit;
			}
			/***
			 * 選択できる決済方法を出力（DBから取得して一覧表示など）
			 * ※サンプルではハードコーディングをしております。
			 */
			$methodKind1 = 'BitCash';
			$methodKind2 = 'クレジットカード';
			?>
			<p><input type="radio" name="methodKind" value="1" checked="checked"><?php print($methodKind1); ?></p>
			<p><input type="radio" name="methodKind" value="2"><?php print($methodKind2); ?></p>
			<br />
			<input type="submit" name="next" value="次へ" />
			<input type="hidden" name="itemCode" value="<?php print($_POST['itemCode']) ?>" />
			<input type="hidden" name="itemName" value="<?php print($itemName) ?>" />
			<input type="hidden" name="itemRating" value="<?php print($itemRating) ?>" />
			<input type="hidden" name="itemPrice" value="<?php print($itemPrice) ?>" />
			<br />
		</form>
	</div>
</body>
</html>