<?php
/**
 * 決済確認-phpサンプル
 *
 * 確認対象のトランザクションID（決済時に払い出される、ユーザ様のお支払情報を一意に特定するID）を
 * 指定することで、対象決済が完了しているかどうかの確認を行います。
 * 通常、決済完了通知は決済の完了時にリアルタイムで通知されますが、当機能を定時実行することで
 * 決済通知機能を利用しない場合や通信障害等により正常に完了通知が受領できなかった場合に代替することが可能です。
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
// 店舗ID（プロパティのshop_idに、契約時にお渡しした店舗IDをご設定ください。）
$shopId = $properties['shop_id'];
// 認証キー（プロパティのshop_idに、契約時にお渡しした認証キーをご設定ください。）
$shopKey = $properties['shop_key'];
// 決済確認APIのURL
$confirmUrl = $properties['confirm_settle_url'];

// 決済確認対象のトランザクションID（決済処理時に払い出される、ユーザ様のお支払情報を一意に特定するIDです。）
// ※サンプルのためハードコーディングしておりますが、適宜データベースからの取得等へご変更ください。
$tranId = '****-*****';

// パラメータ文字列の設定
$param = array(
    'shop_id' => $shopId,
    'shop_key' => $shopKey,
	'tran_id' => $tranId
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

// 生成したパラメータをPOSTする
$contents = file_get_contents($confirmUrl, false, stream_context_create($options));

// レスポンスを分割し、配列に格納する
$responceParam = explode('&', $contents);
for ($paramCount = 0; $paramCount < count($responceParam); $paramCount++) {
	// レスポンスパラメータをさらに"="で分割する
	$responceParams = explode('=', $responceParam[$paramCount]);
	// 左辺を配列キーに、右辺を値に格納する
	$map[$responceParams[0]] = $responceParams[1];
}

/***
 *	決済確認結果（ステータスコード、ステータス文字列の詳細については導入マニュアルをご参照ください。）
 *		status_code				処理結果を示すステータスコード
 *		status_string			処理結果を示すステータス文字列
 *		settle_id				決済コード
 *		price					決済金額
 *		settle_date				決済日時（yyyy/mm/dd h24:mi:ss 形式）
 *		refunded				返金フラグ（返金済みの場合は"true"、未返金の場合は"false"）
 *		cno						管理番号
 */

/***
 * 受信パラメータ出力（証跡として出力）
 */
// CSVファイル名の設定
$csvFile = $properties['confirm_param_file_path'];

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

	// ヘッダ
	$csvHead .= 'status_code,';
	$csvHead .= 'status_string,';
	$csvHead .= 'settle_id,';
	$csvHead .= 'price,';
	$csvHead .= 'settle_date,';
	$csvHead .= 'refunded,';
	$csvHead .= 'cno' . "\r\n";

	// ファイルに書き込み
	fwrite($fp, $csvHead);

	// CSVファイルを閉じる
	fclose($fp);
}
// CSVファイルを追記モードで開く
$fp = fopen($csvFile, 'a');

// CSVファイルを排他ロックする
flock($fp, LOCK_EX);

// データ
$csvData .= $map['status_code'] . ',';
$csvData .= $map['status_string'] . ',';
$csvData .= $map['settle_id'] . ',';
$csvData .= $map['price'] . ',';
$csvData .= $map['settle_date'] . ',';
$csvData .= $map['refunded'] . ',';
$csvData .= $map['cno'] . "\r\n";

// ファイルに書き込み
fwrite($fp, $csvData);

// CSVファイルを閉じる
fclose($fp);

if( $map['status_string'] == 'SUCCESS') {
	// 決済確認が正常に完了した場合

	/***
 	* 加盟店様側処理
	* ※決済確認完了時に行う、加盟店様側処理をご記述ください。
	*/

}