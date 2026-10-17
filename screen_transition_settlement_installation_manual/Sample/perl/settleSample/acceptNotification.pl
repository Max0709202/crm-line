#!/usr/bin/perl
use strict;
use warnings;
use CGI;
use Digest::SHA1  qw(sha1_hex);

###
# 完了通知受領-perlサンプル
#
# 画面遷移型BitCash決済サービスでは、
# BitCashのひらがなID入力画面等の決済画面はBitCash決済システムがエンドユーザに対して出力（表示）します。
# エンドユーザがひらがなIDを入力、またはマイビットキャッシュにログインする事で決済処理（BitCashの残高を減算）が行われ、
# 決済処理完了直後に加盟店様の指定のURLを呼び出し決済完了情報を通知します。
# BitCash決済システムから加盟店様指定のURLへの通知はHTTP(S) POSTメソッドを利用して行います。
# 加盟店様側指定のURLで通知を受領できた場合はHTTPレスポンスコードで200（OK）を返却します。
# HTTPレスポンスコードが200以外の場合は通知を受領できなかったものとして規定時間（約5分間）の間は通知のリトライを行います。
# 完了通知はBitCash決済システムから加盟店様システム(指定のURL)に対して行われるため、
# FireWall等で通信を制限している場合はBitCashシステムからの通信を許可するようにしてください。
# 加盟店様のセキュリティポリシー等により外部からのインバウンド通信を許可できないような場合は、
# 完了通知を利用せずに「決済確認処理」で代替する事も可能です。
# ただし、その場合はリアルタイム性が損なわれる事にご注意ください。
# @copyright	Copyright (c) 2014 BitCash Inc. All Rights Reserved.
# @author		BitCash
# @since		2014/1/10
#
# < 免責事項 >
# ①サンプルプログラムはBitCash決済を導入するための参考として、弊社が十分に動作確認を行った上で提供しておりますが、
# 加盟店様のご利用環境によっては利用できない場合があります。
# ご利用は加盟店様ご自身の責任において行っていただきますようお願いいたします。
# 
# ②本サンプルプログラムのご利用により発生した、いかなるトラブルや損失・損害等につきましては一切責任を負わないものとします。
# 
# ③本サンプルプログラムに不備があった場合でも、弊社は修正およびサポートの義務を負いかねます。
# 
# ④本サンプルプログラムに関する財産権、所有権、知的財産権、その他一切の権限は、弊社に帰属します。

print"Content-type: text/html; charset=UTF-8\n\n";

## 認証キー（契約時にお渡しした通知キーをご設定ください。）
my $notifyKey = 'SAMPLE_notify_key';

###
# ビットキャッシュから送信されるパラメータ
# tran_id		決済を一意に識別できるトランザクションID
# order_id		決済開始処理にて加盟店様から送信された発注コード
# settle_date	決済日時（yyyy/mm/dd h24:mi:ss形式）
# price			決済金額
# hash			パラメータ改ざん検知のためのハッシュ値
#
my $param = new CGI;
my $tranId = $param -> param('tran_id');
my $orderId = $param -> param('order_id');
my $settleDate = $param -> param('settle_date');
my $price = $param -> param('price');
my $hash = $param -> param('hash');

###
# 受信パラメータを出力（証跡として出力）
#
my $rcvCsvFilePath = '/logs/notify_settle_complete_receive_param.csv';
my $rcvCsvFile;
if( !-e $rcvCsvFilePath ) {
	# ファイルが存在しない場合
	# ヘッダー出力
	open($rcvCsvFile, '+>', $rcvCsvFilePath) or die($!);
	print $rcvCsvFile 'tran_id,';
	print $rcvCsvFile 'order_id,';
	print $rcvCsvFile 'settle_date,';
	print $rcvCsvFile 'price,';
	print $rcvCsvFile 'hash';
	print $rcvCsvFile "\r\n";
	close $rcvCsvFile;
}

# データ出力
open($rcvCsvFile, '>>', $rcvCsvFilePath) or die($!);
print $rcvCsvFile $tranId . ',';
print $rcvCsvFile $orderId . ',';
print $rcvCsvFile $settleDate . ',';
print $rcvCsvFile $price . ',';
print $rcvCsvFile $hash;
print $rcvCsvFile "\r\n";
close $rcvCsvFile;

###
# 改ざんチェック
# 悪意のある第三者が不正なパラメータを設定しリクエストを送信するケースがあるため
# ハッシュ値を用いてチェックを行い、不正な呼び出しや改ざんを防止します。
#

# 受信したパラメータからハッシュ値を作成
sub urlEncode {
	my $encodedStr = $_[0];
	$encodedStr =~ s/([^\w -.~])/'%'. uc(unpack('H2', $1))/ego;
	$encodedStr =~ tr/ /+/;
	return $encodedStr;
}

my $hashSource = '';
$hashSource = 'tran_id=' .
			&urlEncode($tranId) .
			'&order_id=' .
			&urlEncode($orderId) .
			'&settle_date=' .
			&urlEncode($settleDate) .
			'&price=' .
			&urlEncode($price) .
			$notifyKey;

$hashSource = sha1_hex($hashSource);

if( $hash != $hashSource ) {
	# ハッシュ値が生成時と異なる場合
	die('改ざんされていました。');
}

###
# 加盟店様側処理
# トランザクションID、オーダーIDを元に購入者および購入情報を特定し
# 商品の受け渡しを行って、支払処理を完了させて下さい。
#



###
# ビットキャッシュへの応答（HTTPレスポンスコード200）
#
print "Status: 200 OK";
