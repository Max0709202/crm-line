<?php
/**
* 決済開始処理-phpサンプル
*
* 決済に必要な情報をBitCash決済システムに対してHTTP(S)プロトコルのGETまたはPOSTメソッドを利用して送信します。
* BitCash決済システムは加盟店様から送信された決済情報を元に決済の準備を行い、
* 処理結果としてトランザクションIDや決済画面のURLを返却します。
* 決済システムからの応答はHTTP(S)のレスポンスボディに
* 「パラメータ名=パラメータ値&パラメータ名=パラメータ値&…」という形式で出力されますので、
* 応答を受信した加盟店様側ではレスポンスボディの文字列を「&」で分割し、
* 更に分割したそれぞれの文字列を「=」で分割する事によりトランザクションIDや決済画面のURLを取得する事ができます。
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
$itemRating = $_POST['itemRating'];
$itemPrice = $_POST['itemPrice'];

// プロパティファイルを取得
$properties = parse_ini_file('./bitcash.properties');
// 店舗ID（プロパティのshop_idに、契約時にお渡しした店舗IDをご設定ください。）
$shopId = $properties['shop_id'];
// 認証キー（プロパティのshop_idに、契約時にお渡しした認証キーをご設定ください。）
$shopKey = $properties['shop_key'];
// 決済準備処理APIのURL
$prepareSettleUrl = $properties['prepare_settle_url'];
// ビットキャッシュ決済画面の表示有効期限の設定(秒単位で設定。サンプルは10分)。任意の期限を設定してください
$timeOut = $properties['timeout'];
// 決済完了通知URL ユーザ様がビットキャッシュ決済画面にてお支払が完了した際、ビットキャッシュより通知を行うURLです
// 定期実行のみでビットキャッシュからのお支払完了通知が不要の場合は、「none」を指定してください
$notifyUrl = $properties['notify_url'];
// お支払完了後、加盟店様サイトへ戻る際のURLです
$returnUrl = $properties['return_url'];
// ユーザ様がビットキャッシュでのお支払の前に加盟店様サイトへ戻る際のURLです
// サンプルでは、商品選択画面を設定しています
$cancelUrl = $properties['cancel_url'];
// オーダーID(注文番号) サンプルのため、現在日時を設定していますが、加盟店様にてユーザ様の支払情報を特定するIDを設定してください
$orderId = 'ORDER_' . time();

// パラメータ文字列の設定
$param = array(
    'shop_id' => $shopId,
    'shop_key' => $shopKey,
	'price' => $itemPrice,
	'rating' => $itemRating,
	'order_id' => $orderId,
	'timeout' => $timeOut,
	'notify_url' => $notifyUrl,
	'return_url' => $returnUrl,
	'cancel_url' => $cancelUrl
);

$param = http_build_query($param, '', '&');
$header = array(
		'Content-Type: application/x-www-form-urlencoded',
		'Content-Length: '.strlen($param)
);

$options = array('http' => array(
    'method' => 'POST',
	'header' => implode("\r\n", $header),
    'content' => $param
));

/***
 * 送信パラメータ出力（証跡として出力）
*/
// CSVファイル名の設定
$sendCsvFile = $properties['start_settle_send_param_file_path'];

// CSVデータの初期化
$sendCsvHead = '';
$sendCsvData = '';

// ファイルの存在確認
if( ! file_exists($sendCsvFile) ) {
	// ファイルが存在しない場合
	touch( $sendCsvFile );

	// CSVファイルを追記モードで開く
	$fp = fopen($sendCsvFile, 'a');

	// CSVファイルを排他ロックする
	flock($fp, LOCK_EX);

	// ヘッダ部
	$sendCsvHead .= 'shop_id,';
	$sendCsvHead .= 'shop_key,';
	$sendCsvHead .= 'price,';
	$sendCsvHead .= 'rating,';
	$sendCsvHead .= 'order_id,';
	$sendCsvHead .= 'timeout,';
	$sendCsvHead .= 'notify_url,';
	$sendCsvHead .= 'return_url,';
	$sendCsvHead .= 'cancel_url' . "\r\n";

	// ファイルに書き込み
	fwrite($fp, $sendCsvHead);

	// CSVファイルを閉じる
	fclose($fp);
}
// CSVファイルを追記モードで開く
$fp = fopen($sendCsvFile, 'a');

// CSVファイルを排他ロックする
flock($fp, LOCK_EX);

// データ部
$sendCsvData .= $shopId . ',';
$sendCsvData .= $shopKey . ',';
$sendCsvData .= $itemPrice . ',';
$sendCsvData .= $itemRating . ',';
$sendCsvData .= $orderId . ',';
$sendCsvData .= $timeOut . ',';
$sendCsvData .= $notifyUrl . ',';
$sendCsvData .= $returnUrl . ',';
$sendCsvData .= $cancelUrl . "\r\n";

// ファイルに書き込み
fwrite($fp, $sendCsvData);

// CSVファイルを閉じる
fclose($fp);

// 生成したパラメータをPOSTする
$contents = file_get_contents($prepareSettleUrl, false, stream_context_create($options));

// レスポンスを分割し、配列に格納する
$responceParam = explode('&', $contents);
for ($paramCount = 0; $paramCount < count($responceParam); $paramCount++) {
	// レスポンスパラメータをさらに"="で分割する
	$responceParams = explode('=', $responceParam[$paramCount]);
	// 左辺を配列キーに、右辺を値に格納する
	$map[$responceParams[0]] = $responceParams[1];
}

/***
 * 受信パラメータ出力（証跡として出力）
 */
// CSVファイル名の設定
$rcvCsvFile = $properties['start_settle_receive_param_file_path'];

// CSVデータの初期化
$rcvCsvHead = '';
$rcvCsvData = '';

// ファイルの存在確認
if( ! file_exists($rcvCsvFile) ) {
	// ファイルが存在しない場合
	touch( $rcvCsvFile );

	// CSVファイルを追記モードで開く
	$fp = fopen($rcvCsvFile, 'a');

	// CSVファイルを排他ロックする
	flock($fp, LOCK_EX);

	// ヘッダ部
	$rcvCsvData .= 'status_code,';
	$rcvCsvData .= 'status_string,';
	$rcvCsvData .= 'tran_id,';
	$rcvCsvData .= 'settle_url' . "\r\n";

	// ファイルに書き込み
	fwrite($fp, $rcvCsvHead);

	// CSVファイルを閉じる
	fclose($fp);
}
// CSVファイルを追記モードで開く
$fp = fopen($rcvCsvFile, 'a');

// CSVファイルを排他ロックする
flock($fp, LOCK_EX);

// データ部
$rcvCsvData .= $map['status_code'] . ",";
$rcvCsvData .= $map['status_string'] . ",";
$rcvCsvData .= $map['tran_id'] . ",";
$rcvCsvData .= $map['settle_url'] . "\r\n";

// ファイルに書き込み
fwrite($fp, $rcvCsvData);

// CSVファイルを閉じる
fclose($fp);

if( $map['status_string'] == 'SUCCESS') {
	/***
	 *	決済準備処理が成功した場合
	 *	status_code:"0"				ステータスコード：処理成功
	 *	status_string:"SUCCEESS"	ステータス文字列：処理成功
	 *	tran_id						決済を一意に識別できるトランザクションID
	 *	settle_url					決済画面URL(暗号化トランザクションID付加)
	 */

	/***
	 * ユーザ様のお支払情報を一意に特定するトランザクションIDを返却します。
	 * 問い合わせ、お支払の取消、お支払の確認にも使用いただけますので、加盟店様システム側で保管してください。
	 * */
	// $tranId = $map['tran_id'];

	// 決済画面URL(settleUrl=)後のパラメータ(決済画面URL)へリクエストをリダイレクトします
	// ※パラメータはエンコードされているためデコードが必要です
	$settleUrl = urldecode($map['settle_url']);
	header("Location: $settleUrl");
	exit;
} else {
	/***
	 * 	決済準備処理が失敗した場合
	 *	status_code:"100",status_string:"INVALID_SHOP_ID"			加盟店IDが不正な場合
	 *	status_code:"101",status_string:"INVALID_SHOP_KEY"			加盟店キーが不正な場合
	 *	status_code:"102",status_string:"INVALID_PRICE"				決済金額が不正な場合
	 *	status_code:"103",status_string:"INVALID_RATING"			商品レイティングが不正な場合
	 *	status_code:"104",status_string:"INVALID_ORDER_ID"			発注IDに利用できない文字が含まれている場合
	 *	status_code:"105",status_string:"INVALID_TIMEOUT"			決済有効期間が不正な場合
	 *	status_code:"106",status_string:"INVALID_NOTIFY_URL"		完了通知URLが不正な場合
	 *	status_code:"107",status_string:"INVALID_RETURN_URL"		正常時戻りURLが不正な場合
	 *	status_code:"108",status_string:"INVALID_CANCEL_URL"		キャンセルURLが不正な場合
	 *	status_code:"200",status_string:"SHOP_KEY_MISMATCH"			加盟店キーが不一致の場合
	 *	status_code:"201",status_string:"OUT_OF_RANGE_PRICE"		決済金額が有効範囲(0～200,000)外の場合
	 *	status_code:"202",status_string:"RATING_MISMATCH"			ST加盟店が商品レイティングにEXを指定した場合
	 *	status_code:"203",status_string:"TOO_LONG_ORDER_ID"			発注IDの文字列長が制限(32文字)を超えている場合
	 *	status_code:"204",status_string:"OUT_OF_RANGE_TIMEOUT"		決済有効期間が有効範囲(0～86400)を超えている場合
	 *	status_code:"300",status_string:"ACCESS_DENIED"				許可されていないアクセス元IPアドレスの場合
	 *	status_code:"301",status_string:"SETTLE_TYPE_MISMATCH"		決済種別が異なる場合(API型決済の加盟店の場合)
	 *	status_code:"302",status_string:"SHOP_CLOSED"				加盟店が開店状態ではない場合
	 *	status_code:"900",status_string:"SYSTEM_ERROR"				その他、システムエラー
	 */
?>
	<html lang="ja" >
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
	<head>
	<title>決済-phpサンプル | 6.決済エラー画面</title>
	</head>
	<body>
		<div id="content">
			<h3>決済-phpサンプル | 6.決済エラー画面</h3>
			<br />
			<h3>お手続きに失敗しました</h3>
			<br />
		</div>
	</body>
	</html>
<?php
}