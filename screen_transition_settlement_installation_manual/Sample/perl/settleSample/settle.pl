#!/usr/bin/perl
use strict;
use warnings;
use CGI;
use Time::HiRes qw(gettimeofday);
use Socket;

###
# 決済開始処理-perlサンプル
#
# 決済に必要な情報をBitCash決済システムに対してHTTP(S)プロトコルのGETまたはPOSTメソッドを利用して送信します。
# BitCash決済システムは加盟店様から送信された決済情報を元に決済の準備を行い、
# 処理結果としてトランザクションIDや決済画面のURLを返却します。
# 決済システムからの応答はHTTP(S)のレスポンスボディに
# 「パラメータ名=パラメータ値&パラメータ名=パラメータ値&…」という形式で出力されますので、
# 応答を受信した加盟店様側ではレスポンスボディの文字列を「&」で分割し、
# 更に分割したそれぞれの文字列を「=」で分割する事によりトランザクションIDや決済画面のURLを取得する事ができます。
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

my $param = new CGI;
my $itemRating = $param -> param('itemRating');
my $itemPrice = $param -> param('itemPrice');

# 店舗ID（契約時にお渡しした店舗IDをご設定ください。）
my $shopId = 'SAMPLE9999';

# 認証キー（契約時にお渡しした認証キーをご設定ください。）
my $shopKey = 'SAMPLE_key';

# ビットキャッシュ決済画面の表示有効期限の設定(秒単位で設定。サンプルは10分)。任意の期限を設定してください
my $timeout = '600';

# 決済完了通知URL ユーザ様がビットキャッシュ決済画面にてお支払が完了した際、ビットキャッシュより通知を行うURLです
# 定期実行のみでビットキャッシュからのお支払完了通知が不要の場合は、「none」を指定してください
my $notifyUrl = 'http://example.jp/settleSample/acceptNotification.pl';

# お支払完了後、加盟店様サイトへ戻る際のURLです
my $returnUrl = 'http://example.jp/settleSample/complete.pl';

# ユーザ様がビットキャッシュでのお支払の前に加盟店様サイトへ戻る際のURLです
# サンプルでは、商品選択画面を設定しています
my $cancelUrl = 'http://example.jp/settleSample/selectItem.pl';

# オーダーID(注文番号) サンプルのため、現在日時を設定していますが、加盟店様にてユーザ様の支払情報を特定するIDを設定してください
my($sec, $microsec) = gettimeofday;
my $orderId = 'ORDER_' . $sec . $microsec;

# API名
my $api = 'act=prepareSettle';

###
# 送信パラメータを出力（証跡として出力）
#

my $sendCsvFilePath = '/logs/start_settle_send_param.csv';
my $sendCsvFile;
if( !-e $sendCsvFilePath ) {
	# ファイルが存在しない場合
	# ヘッダー出力
	open($sendCsvFile, '+>', $sendCsvFilePath) or die($!);
	print $sendCsvFile 'shop_id,';
	print $sendCsvFile 'shop_key,';
	print $sendCsvFile 'price,';
	print $sendCsvFile 'rating,';
	print $sendCsvFile 'order_id,';
	print $sendCsvFile 'timeout,';
	print $sendCsvFile 'notify_url,';
	print $sendCsvFile 'return_url,';
	print $sendCsvFile 'cancel_url';
	print $sendCsvFile "\r\n";
	close $sendCsvFile;
}

# データ出力
open($sendCsvFile, '>>', $sendCsvFilePath) or die($!);
print $sendCsvFile $shopId . ',';
print $sendCsvFile $shopKey. ',';
print $sendCsvFile $itemPrice . ',';
print $sendCsvFile $itemRating . ',';
print $sendCsvFile $orderId . ',';
print $sendCsvFile $timeout .',';
print $sendCsvFile $notifyUrl . ',';
print $sendCsvFile $returnUrl . ',';
print $sendCsvFile $cancelUrl;
print $sendCsvFile "\r\n";
close $sendCsvFile;

###
# 決済準備処理APIへHTTPリクエストを送信
#
my $host = "settle.bitcash.co.jp";
my $port = "80";
my $path = "/settle/tri";
#パラメータ文字列の設定
my $query = $api .
		"&shop_id=" .
		$shopId .
		"&shop_key=" .
		$shopKey .
		"&price=" .
		$itemPrice .
		"&rating=" .
		$itemRating .
		"&order_id=" .
		$orderId .
		"&timeout=" .
		$timeout .
		"&notify_url=" .
		$notifyUrl .
		"&return_url=" .
		$returnUrl .
		"&cancel_url=" .
		$cancelUrl;

my $len = length($query);

 # ソケットのオープン
if (!socket(SOCK, PF_INET, SOCK_STREAM, getprotobyname('tcp'))) {
	return "error:open socket";
}

# ポートへの接続
if (!connect(SOCK, sockaddr_in($port, inet_aton($host) ) ) ) {
	close(SOCK);
	return "error:connect socket";
}

# ソケットのバッファリングを停止
select(SOCK);
$| = 1;
select(STDOUT);

# HTTP 要求ヘッダー
# POST メソッドで要求します。
print SOCK "POST $path HTTP/1.0" . "\r\n";
print SOCK "Content-type: application/x-www-form-urlencoded" . "\r\n";
print SOCK "Host: $host" . "\r\n";
# Content-Length: を送る（クエリーのバイト数）
# ヘッダーの最後は２回改行する
print SOCK "Content-Length: $len" . "\r\n\r\n";

# POST 内容送信
print SOCK "$query";

# レスポンスボディの内容を取得
while(<SOCK>) {
	last if m/^\r\n$/;
}
my $contents = <SOCK>;
close(SOCK);

# レスポンスパラメータを分割し、配列に格納する
my @responceParam = split(/&/, $contents);
my %map;
foreach my $var(@responceParam) {
	# レスポンスパラメータをさらに"="で分割する
	my($key, $value) = split(/=/, $var);
	# 左辺を配列キーに、右辺を値に格納する
	$map{$key} = $value;
}

###
# 受信パラメータを出力（証跡として出力）
#
my $rcvCsvFilePath = '/logs/start_settle_receive_param.csv';
my $rcvCsvFile;
if( !-e $rcvCsvFilePath ) {
    # ファイルが存在しない場合
    # ヘッダー出力
    open($rcvCsvFile, '+>', $rcvCsvFilePath) or die($!);
    print $rcvCsvFile 'status_code,';
    print $rcvCsvFile 'status_string,';
    print $rcvCsvFile 'tran_id,';
    print $rcvCsvFile 'settle_url,';
    print $rcvCsvFile "\r\n";
    close $rcvCsvFile;
}

# データ出力
open($rcvCsvFile, '>>', $rcvCsvFilePath) or die($!);
print $rcvCsvFile $map{'status_code'} . ',';
print $rcvCsvFile $map{'status_string'} . ',';
print $rcvCsvFile $map{'tran_id'} . ',';
print $rcvCsvFile $map{'settle_url'} . ',';
print $rcvCsvFile "\r\n";
close $rcvCsvFile;

if( $map{'status_string'} eq 'SUCCESS' ) {
	###
	# 決済準備処理が成功した場合
	# status_code:"0"			ステータスコード：処理成功
	# status_string:"SUCCEESS"	ステータス文字列：処理成功
	# tran_id					決済を一意に識別できるトランザクションID
	# settle_url				決済画面URL(暗号化トランザクションID付加)
	#

	# ユーザ様のお支払情報を一意に特定するトランザクションIDを返却します。
	# 問い合わせ、お支払の取消、お支払の確認にも使用いただけますので、加盟店様システム側で保管してください。
	my $tranId = $map{'tran_id'};

	# 決済画面URL(settleUrl=)後のパラメータ(決済画面URL)へリクエストをリダイレクトします
	# ※パラメータはエンコードされているためデコードが必要です
	my $settleUrl = $map{'settle_url'};
	$settleUrl =~ tr/+/ /;
	$settleUrl =~ s/%([0-9A-Fa-f][0-9A-Fa-f])/pack('H2', $1)/eg;
	print "Location: $settleUrl\n\n";
	exit;
} else {
	###
	# 決済準備処理が失敗した場合
	# status_code:"100",status_string:"INVALID_SHOP_ID"			加盟店IDが不正な場合
	# status_code:"101",status_string:"INVALID_SHOP_KEY"		加盟店キーが不正な場合
	# status_code:"102",status_string:"INVALID_PRICE"			決済金額が不正な場合
	# status_code:"103",status_string:"INVALID_RATING"			商品レイティングが不正な場合
	# status_code:"104",status_string:"INVALID_ORDER_ID"		発注IDに利用できない文字が含まれている場合
	# status_code:"105",status_string:"INVALID_TIMEOUT"			決済有効期間が不正な場合
	# status_code:"106",status_string:"INVALID_NOTIFY_URL"		完了通知URLが不正な場合
	# status_code:"107",status_string:"INVALID_RETURN_URL"		正常時戻りURLが不正な場合
	# status_code:"108",status_string:"INVALID_CANCEL_URL"		キャンセルURLが不正な場合
	# status_code:"200",status_string:"SHOP_KEY_MISMATCH"		加盟店キーが不一致の場合
	# status_code:"201",status_string:"OUT_OF_RANGE_PRICE"		決済金額が有効範囲(0～200,000)外の場合
	# status_code:"202",status_string:"RATING_MISMATCH"			ST加盟店が商品レイティングにEXを指定した場合
	# status_code:"203",status_string:"TOO_LONG_ORDER_ID"		発注IDの文字列長が制限(32文字)を超えている場合
	# status_code:"204",status_string:"OUT_OF_RANGE_TIMEOUT"	決済有効期間が有効範囲(0～86400)を超えている場合
	# status_code:"300",status_string:"ACCESS_DENIED"			許可されていないアクセス元IPアドレスの場合
	# status_code:"301",status_string:"SETTLE_TYPE_MISMATCH"	決済種別が異なる場合(API型決済の加盟店の場合)
	# status_code:"302",status_string:"SHOP_CLOSED"				加盟店が開店状態ではない場合
	# status_code:"900",status_string:"SYSTEM_ERROR"			その他、システムエラー
	#
print <<"EOM";
Content-Type: text/html; charset=UTF-8\n\n
<html lang="ja" >
<head>
<title>決済-perlサンプル | 6.決済エラー画面</title>
</head>
<body>
<div id="content">
<h3>決済-perlサンプル | 6.決済エラー画面</h3>
<br />
<h3>お手続きに失敗しました</h3>
</div>
</body>
</html>
EOM
exit;
}
