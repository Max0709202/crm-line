#!/usr/bin/perl
use strict;
use warnings;

###
# 決済-perlサンプル
#
# 購入商品選択画面のサンプルです。
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

###
# 選択できる商品を出力（DBから取得して一覧表示など）
# ※サンプルではハードコーディングをしております。
#
my $item1 = '商品A：1,000円（全年齢）';
my $item2 = '商品B：3,000円（全年齢）';
my $item3 = '商品C：5,000円（全年齢）';
my $item4 = '商品D：1,000円（20歳以上）';
my $item5 = '商品E：3,000円（20歳以上）';
my $item6 = '商品F：5,000円（20歳以上）';

print <<"EOM";
Content-Type: text/html; charset=UTF-8\n\n
<html lang="ja" >
<head>
<title>決済-perlサンプル | 1.購入商品選択画面</title>
</head>
<body>
<div id="content">
<h3>決済-perlサンプル | 1.購入商品選択画面</h3>
<form method="post" action="selectMethod.pl">
<br />
<p><input type="radio" name="itemCode" value="1" checked="checked">$item1</p>
<p><input type="radio" name="itemCode" value="2">$item2</p>
<p><input type="radio" name="itemCode" value="3">$item3</p>
<p><input type="radio" name="itemCode" value="4">$item4</p>
<p><input type="radio" name="itemCode" value="5">$item5</p>
<p><input type="radio" name="itemCode" value="6">$item6</p>
<input type="submit" name="next" value="次へ" />
<br />
</form>
</div>
</body>
</html>
EOM
exit;