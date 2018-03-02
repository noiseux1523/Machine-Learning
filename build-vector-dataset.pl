#!/usr/bin/perl
use Carp;
use Getopt::Long;
use Data::Dumper

# Script to modify the tokenized dataset to vectorized dataset
#
# example
#
# ./build-vector-dataset.pl 
# --ds dataset.txt
# --v vector.txt   >   vector-dataset.txt

$|=1; # flush stdin stdout 

# VARIABLES
my $ds=();
my $v=();
my $word_nb=();
my $v_size=();
my %vectors=();
my $verbose=();
my $DBG=0;
my $help=();
my $helpMsg = "Build dataset for neural network training
                  \n Usage:  $0
                  \t --ds       dataset file                    |   --ds = dataset
                  \t --v        vectors file                    |   --v  = vectors
                  \t --verbose  print more info
                  \t --DEBG     k                               |   --DBG = k debug level
                  \t --help     his help\n\n";

# OPTIONS
GetOptions( "verbose"  => \$verbose,	   # --verbose
            "DBG=i"    => \$DBG,	       # --DBG 2 or --DBG=3
            "ds=s"     => \$ds,          # --file 
            "v=s"      => \$v,           # --file
            "help+"    => \$help );      # if --help print a message it does not require arguments

unless (!defined($help)) {
    print $helpMsg;
    exit(0);
}

# OPEN VECTORS FILE
my $firstline = 1;
my @vector = ();
my @vector_values = ();

open (FH, $v) or die "Unable to read vector file $v\n";
while (my $line = <FH>) {

  # SKIP FIRST LINE (HEADER)
  if ($firstline) {
    @file = split(" ", $line);
    $word_nb = $file[0];
    $v_size = $file[1];
    $firstline = 0;

  # SPLIT WORD FROM VECTOR VALUES
  } else {
    chomp($line);
    @vector = split(" ", $line);
    $word = $vector[0];
    @vector_values = @vector[1 .. $#vector];

    # PRINT WORD AND VECTOR VALUES IN HASH TABLE
    $index = 0;
    foreach (@vector_values) {
      $vectors{$word}[$index] = $_;
      $index++;
    }
  }
}
close(FH);

#print Dumper(\%vectors);

# OPEN DATASET FILE
open (FH, $ds) or die "Unable to read dataset $ds";
$count = `wc -l < $ds`;
chomp($count);
while (my $line = <FH>) {
  #print STDERR "$./$count\n";

    # READ EACH LINE (ONE LINE = ONE FILE)
    chomp($line);
    @file = split(" ", $line);
    $counter = 0;

    # CHANGE EACH WORD FOR ITS VECTORS (LENGTH = 200)
    foreach (@file) {
      $word = $_;
      for ($i = 0; $i < $v_size; $i++) {
        print "$vectors{$word}[$i] ";
      }
        print "\n";
    }

    # ADD THE </S> VECTORS WHEN A LINE IS FINISHED PROCESSING (SO WHEN THE FILE ENDS)
    $end_file = "</s>";
    for ($i = 0; $i < $v_size; $i++) {
      print "$vectors{$end_file}[$i] ";
    }
    print "\n";

    # ADD NEW LINE FOR THE NEXT FILE
    # print "\n";
}
close(FH);








