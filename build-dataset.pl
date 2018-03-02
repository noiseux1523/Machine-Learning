#!/usr/bin/perl
use Carp;
use Getopt::Long;
use strict;

# Script to build the dataset for feed-forward neural network
#
# example
#
# ./build-dataset.pl 
# --tr  commons-jcs-results/706E7BAE5D20D5F463F53F28B9652DD8.nncts > commons-jcs-results/706E7BAE5D20D5F463F53F28B9652DD8.vectors

$|=1; # flush stdin stdout 

# VARIABLES
my $tr=();
my $verbose=();
my $DBG=0;
my $help=();
my $helpMsg = "Build dataset for neural network training
                  \n Usage:  $0
                  \t --tr       file of training transactions   |   --trlog = ffile of training transactions
                  \t --verbose  print more info
                  \t --DEBG     k                               |   --DBG = k debug level
                  \t --help     his help\n\n";

# OPTIONS
GetOptions( "verbose"  => \$verbose,	   # --verbose
            "DBG=i"    => \$DBG,	       # --DBG 2 or --DBG=3
            "tr=s"     => \$tr,          # --file 
            "help+"    => \$help );      # if --help print a message it does not require arguments

unless (!defined($help)) {
    print $helpMsg;
    exit(0);
}

# OPEN TRANSACTION FILE
open (FH, $tr) or die" Unable to read transaction file $tr\n";
my @tr=<FH>;
close(FH);
chop(@tr);

# READ TRANSACTION FILE
foreach my $t (@tr) {
  (my $status, my $before, my $current, my $after, my $file) = split(/;/,$t);
  $file =~ s/\r|\n//g;
  if ($status eq "created") {

    # OPEN SMELL RESULT FILE (with hash in transaction file)
    my $filename = "xerces2-j/smell-$before.txt";
    open (FH, $filename) or die "Unable to read transaction file: $tr\n";
    my @smells = <FH>;
    close(FH);
    chop(@smells);

    # FIND RELEVANT FILE WITH SATD IN SMELL RESULT FILE (with file in transaction file)
    my $entity = ();
    my $extension = ();
    my @path = ();
    my @metrics = ();
    my @entity_path = ();

    foreach my $s (@smells) {

      if ($s =~ /(.*):::(.*):::(.*)/) {
        if ($2 eq $file) {
          @path = split("/",$2);
          ($entity,$extension) = split(/\./,$path[$#path]);
        }
      }

      if (defined $entity) {
        @metrics = split(/;/,$s);
        @entity_path = split(/\./,$metrics[$#metrics]);
        if ($entity eq $entity_path[$#entity_path]) {
          print "$s\n";
          last;
        }
      }
    }
    # PRINT/CONCATENATE THE LIST OF METRICS
    # DO THIS FOR THE HASH TRIPLET OF EACH LINE IN TRANSACTION FILE
  # ADD THE LABEL, 1 IF SATD APPEARS OR 0 IF SATD DISAPPEARS, TO THE END OF THE METRICS + ADD A NEWLINE
# DO THIS FOR ALL THE LINES IN TRANSACTION FILE
  }
}















