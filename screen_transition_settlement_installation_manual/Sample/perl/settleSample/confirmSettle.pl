#!/usr/bin/perl
use strict;
use warnings;
use Socket;

###
# 決済確認-perlサンプル
#
# 確認対象のトランザクションID（決済時に払い出される、ユーザ様のお支払情報を一意に特定するID）を
# 指定することで、対象決済が完了しているかどうかの確認を行います。
# 通常、決済完了通知は決済の完了時にリアルタイムで通知されますが、当機能を定時実行することで
# 決済通知機能を利用しない場合や通信障害等により正常に完了通知が受領できなかった場合に代替することが可能です。
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

# 店舗ID（契約時にお渡しした店舗IDをご設定ください。）
my $shopId = 'SAMPLE9999';

# 認証キー（契約時にお渡しした認証キーをご設定ください。）
my $shopKey = 'SAMPLE_key';

# 決済確認API名
my $api = 'act=confirmSettle';

# 決済確認対象のトランザクションID（決済処理時に払い出される、ユーザ様のお支払情報を一意に特定するIDです。）
# ※サンプルのためハードコーディングしておりますが、適宜データベースからの取得等へご変更ください。
my $tranId = 'SAMPLE-000001';

###
# 決済確認APIへHTTPリクエストを送信
#
print "Content-type: text/html; charset=UTF-8\n\n";
my $host = "settle.bitcash.co.jp";
my $port = "80";
my $path = "/settle/tri";
# パラメータ文字列の設定
my $query = $api . "&shop_id=" . $shopId . "&shop_key=" . $shopKey . "&tran_id=" . $tranId;

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
$contents = <SOCK>;
close(SOCK);

# 受信パラメータを分割し、配列に格納する
my @responceParam = split(/&/, $contents);
my %map;
foreach my $var(@responceParam) {
	# レスポンスパラメータをさらに"="で分割する
	my ($key, $value) = split(/=/, $var);
	# 左辺を配列キーに、右辺を値に格納する
	$map{$key} = $value;
}

###
# 決済確認結果（ステータスコード、ステータス文字列の詳細については導入マニュアルをご参照ください。）
# status_code			処理結果を示すステータスコード
# status_string			処理結果を示すステータス文字列
# settle_id				決済コード
# price					決済金額
# settle_date			決済日時（yyyy/mm/dd h24:mi:ss 形式）
# refunded				返金フラグ（返金済みの場合は"true"、未返金の場合は"false"）
# cno					管理番号
#

###
# 受信パラメータを出力（証跡として出力）
#
my $rcvCsvFilePath = '/logs/confirm_receive_param.csv';
my $rcvCsvFile;
if( !-e $rcvCsvFilePath ) {
    # ファイルが存在しない場合
    # ヘッダー出力
    open($rcvCsvFile, '+>', $rcvCsvFilePath) or die($!);
    print $rcvCsvFile 'status_code,';
    print $rcvCsvFile 'status_string,';
    print $rcvCsvFile 'settle_id,';
    print $rcvCsvFile 'price,';
    print $rcvCsvFile 'settle_date,';
    print $rcvCsvFile 'refunded,';
    print $rcvCsvFile 'cno';
    print $rcvCsvFile "\r\n";
    close $rcvCsvFile;
}

# データ出力
open($rcvCsvFile, '>>', $rcvCsvFilePath) or die($!);
print $rcvCsvFile $map{'status_code'} . ',';
print $rcvCsvFile $map{'status_string'} . ',';
print $rcvCsvFile $map{'settle_id'} . ',';
print $rcvCsvFile $map{'price'} . ',';
print $rcvCsvFile $map{'settle_date'} . ',';
print $rcvCsvFile $map{'refunded'} . ',';
print $rcvCsvFile $map{'cno'};
print $rcvCsvFile "\r\n";
close $rcvCsvFile;

if( $map{'status_string'} eq 'SUCCESS' ) {
	# 決済確認が正常に完了した場合

	###
 	# 加盟店様側処理
	# ※決済確認完了時に行う、加盟店様側処理をご記述ください。
	#

}
exit;
