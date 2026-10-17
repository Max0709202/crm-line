#!/usr/bin/perl
use strict;
use warnings;
use CGI;

###
# 決済-perlサンプル
#
# 支払確認画面のサンプルです。
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
my $itemCode = $param -> param('itemCode');
my $itemName = $param -> param('itemName');
my $itemRating = $param -> param('itemRating');
my $itemPrice = $param -> param('itemPrice');

###
# リクエストで受け取った決済種別を元に決済情報を取得（DBから取得など）
# ※サンプルではハードコーディングをしております。
#
my $methodKind = $param -> param('methodKind');
my $action;
my $methodName;

if ($methodKind == 1) {
	#決済方法：BitCashの場合
	$action = 'settle.pl';
	$methodName = 'BitCash';
} else {
	print "Location: selectMethod.pl\n\n";
	exit;
}

print <<"EOM";
Content-Type: text/html; charset=UTF-8\n\n
<html lang="ja" >
<head>
<title>決済-perlサンプル | 3.支払確認画面</title>
</head>
<body>
<div id="content">
<h3>決済-perlサンプル | 3.支払確認画面</h3>
<form method="post" action=$action>
<br />
<p>商品名：$itemName
<p>お支払い金額：$itemPrice
<p>お支払方法：$methodName
<br />
<br />
<input type="submit" name="next" value="決済" />
<input type="hidden" name="itemCode" value="$itemCode" />
<input type="hidden" name="itemName" value="$itemName" />
<input type="hidden" name="itemRating" value="$itemRating" />
<input type="hidden" name="itemPrice" value="$itemPrice" />
<br />
</form>
</div>
</body>
</html>
EOM
exit;