<?php
/**
 * 完了通知受領-phpサンプル
 *
 * 画面遷移型BitCash決済サービスでは、
 * BitCashのひらがなID入力画面等の決済画面はBitCash決済システムがエンドユーザに対して出力（表示）します。
 * エンドユーザがひらがなIDを入力、またはマイビットキャッシュにログインする事で決済処理（BitCashの残高を減算）が行われ、
 * 決済処理完了直後に加盟店様の指定のURLを呼び出し決済完了情報を通知します。
 * BitCash決済システムから加盟店様指定のURLへの通知はHTTP(S) POSTメソッドを利用して行います。
 * 加盟店様側指定のURLで通知を受領できた場合はHTTPレスポンスコードで200（OK）を返却します。
 * HTTPレスポンスコードが200以外の場合は通知を受領できなかったものとして規定時間（約5分間）の間は通知のリトライを行います。
 * 完了通知はBitCash決済システムから加盟店様システム(指定のURL)に対して行われるため、
 * FireWall等で通信を制限している場合はBitCashシステムからの通信を許可するようにしてください。
 * 加盟店様のセキュリティポリシー等により外部からのインバウンド通信を許可できないような場合は、
 * 完了通知を利用せずに「決済確認処理」で代替する事も可能です。
 * ただし、その場合はリアルタイム性が損なわれる事にご注意ください。
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
// プロパティファイルを取得
$properties = parse_ini_file('./bitcash.properties');

/***
 *	ビットキャッシュから送信されるパラメータ
 *	tran_id			決済を一意に識別できるトランザクションID
 *	order_id		決済開始処理にて加盟店様から送信された発注コード
 *	settle_date		決済日時（yyyy/mm/dd h24:mi:ss形式）
 *	price			決済金額
 *	hash			パラメータ改ざん検知のためのハッシュ値
 */
$tranId = $_POST['tran_id'];
$orderId = $_POST['order_id'];
$settleDate = $_POST['settle_date'];
$price = $_POST['price'];
$hash = $_POST['hash'];

/***
 * 受信パラメータ出力（証跡として出力）
*/
// CSVファイル名の設定
$csvFile = $properties['notify_settle_complete_receive_param_file_path'];

// CSVデータの初期化
$csvHead = '';
$csvData = '';

// ファイルの存在確認
if( ! file_exists($csvFile) ) {
	// ファイルが存在しない場合
	touch( $csvFile );

	// CSVファイルを追記モードで開く
	$fp = fopen($csvFile, 'a');

	// CSVファイルを排他ロックする
	flock($fp, LOCK_EX);

	// ヘッダ部
	$csvHead .= 'tran_id,';
	$csvHead .= 'order_id,';
	$csvHead .= 'settle_date,';
	$csvHead .= 'price,';
	$csvHead .= 'hash' . "\r\n";

	// ファイルに書き込み
	fwrite($fp, $csvHead);

	// CSVファイルを閉じる
	fclose($fp);
}
// CSVファイルを追記モードで開く
$fp = fopen($csvFile, 'a');

// CSVファイルを排他ロックする
flock($fp, LOCK_EX);

// データ部
$csvData .= mb_convert_encoding($tranId, 'UTF-8', 'auto') . ',';
$csvData .= mb_convert_encoding($orderId, 'UTF-8', 'auto') . ',';
$csvData .= mb_convert_encoding($settleDate, 'UTF-8', 'auto') . ',';
$csvData .= mb_convert_encoding($price, 'UTF-8', 'auto') . ',';
$csvData .= mb_convert_encoding($hash, 'UTF-8', 'auto') . "\r\n";

// ファイルに書き込み
fwrite($fp, $csvData);

// CSVファイルを閉じる
fclose($fp);

/***
 * 改ざんチェック
 * 悪意のある第三者が不正なパラメータを設定しリクエストを送信するケースがあるため
 * ハッシュ値を用いてチェックを行い、不正な呼び出しや改ざんを防止します。
 */
// 受信したパラメータからハッシュ値を作成
$hashSource = '';
$hashSource .= 'tran_id=';
$hashSource .= urlencode(mb_convert_encoding($tranId, 'UTF-8', 'auto'));
$hashSource .= '&order_id=';
$hashSource .= urlencode(mb_convert_encoding($orderId, 'UTF-8', 'auto'));
$hashSource .= '&settle_date=';
$hashSource .= urlencode(mb_convert_encoding($settleDate, 'UTF-8', 'auto'));
$hashSource .= '&price=';
$hashSource .= urlencode(mb_convert_encoding($price, 'UTF-8', 'auto'));
$hashSource .= $properties['notify_key'];

$hashSource = sha1($hashSource);

if( $hash != $hashSource ) {
	// ハッシュ値が生成時と異なる場合
	throw new Exception('改ざんされていました。');
}

/***
 * 加盟店様側処理
 * トランザクションID、オーダーIDを元に購入者および購入情報を特定し
 * 商品の受け渡しを行って、支払処理を完了させて下さい。
 */



/***
 * ビットキャッシュへの応答（HTTPレスポンスコード200）
 */
header('HTTP/1.1 200 OK');