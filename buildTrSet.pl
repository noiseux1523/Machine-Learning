#!/usr/bin/perl
use Carp;
use Getopt::Long;
use strict;
#
# example
#
# ./buildTrSet.pl 
# --tr  commons-jcs-results/706E7BAE5D20D5F463F53F28B9652DD8.sorted  
# --satd commons-jcs-results/706E7BAE5D20D5F463F53F28B9652DD8.htr 
# --delta commons-jcs-results/706E7BAE5D20D5F463F53F28B9652DD8-satd-delta.csv > commons-jcs-results/706E7BAE5D20D5F463F53F28B9652DD8.cts

$|=1;				 # flush stdin stdout 

my $tr=();
my $verbose=();
my $satd=0;
my $DBG=0;
my $help=();
my $delta=0;

my $helpMsg = "match sorted  in time git transactions with SATD hashes

\nUsage:  $0




\t --tr file of sorted transactions CSV |   --trlog= file of sorted transactions  CSV
\t --satd  file of satd transactions one per line  |   --satd=file of satd transactions one per line
\t --verbose print more into like lifelanes
\t --DEBG   k |  --DBG=k debug level
\t --help  this help\n\n";

GetOptions( "verbose"  => \$verbose,	   # --verbose
            "DBG=i"    => \$DBG,	   # --DBG 2 or --DBG=3
            "tr=s" => \$tr,	   # --file pippo  or --file=pippo
            "satd=s" => \$satd,	   # --file pippo  or --file=pippo
            "delta=s" => \$delta,
            "help+"    => \$help ); # if --help print a message it does not require arguments

# process args

unless (!defined($help)) {
    print $helpMsg;
    exit(0);
}

print STDERR "Transaction sorted:  $tr";

#exit(0);
open (FH, $satd) or die" Unable to read transaction file $satd\n";
my @satd=<FH>;
close(FH);
chop(@satd);

my @tra=();
open (FH, $tr) or die" Unable to read transaction file $tr\n";
while (<FH>) {
  chop();
  my ($t,$d,$u)=split(";");
  $tra[$#tra+1]=$t;
}
close(FH);

open (FH, $delta) or die" Unable to read transaction file $delta\n";
my @delta=<FH>;
close(FH);
chop(@delta);

my %h=();
print STDERR  "\nTransactions done\n";
foreach my $t (@satd) {
# print "Satd:$t\n";
  for (my $k = 0; $k <= $#tra; $k++) {
    if ($tra[$k] eq $t) {
      # print "\t$k\n";
      if ($k > 0 && $k < $#tra) {
        foreach my $d (@delta) {
            (my $st1, my $st2, my $val1, my $val2, my $trans) = split(";",$d);
            if ($trans eq $t) {
              # print "$t; $tra[$k-1]; $tra[$k+1]\n";
              # $h{$t}=$t;
              # $h{$tra[$k-1]}=$tra[$k-1];
              # $h{$tra[$k+1]}=$tra[$k+1];
              print "$tra[$k-1];$val1\n"; # 1 ou 0 en plus dependemment de $t
              print "$t;$val2\n";         # 1 ou 0 en plus selon ce qu'on a dans satd-delta.csv
              print "$tra[$k+1];$val2\n"; #1 ou 0 en plus dependemment de $t
          }
        }
      } else {
          # ===== NOT PRINTING because no transaction before/after (first and last commit) =====
          # warn  "Wrong transaction $t position\n";
          #	print "$t\n"
          # $h{$t}=$t;
          # print "$t\n"; 
      }
    }
  }
}

# foreach my $k (keys %h){
#   print "$k\n";
# }

exit(0);


