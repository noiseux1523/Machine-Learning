#!/usr/bin/perl

use Getopt::Long;
use List::Util qw(shuffle);
use Math::Round;
use Tie::File;

#
# EXAMPLE
#
# perl link-antipatterns.pl --ap antipattern-list.txt --metrics metrics-list.txt
#
# --ap antipattern-list.txt 
# --metrics metrics-list.txt
#

my $antipatterns_name=();
my $metrics_name=();

# All options available
GetOptions( "verbose"    => \$verbose,	 
            "DBG=i"      => \$DBG,	  
            "ap=s"       => \$antipatterns_name,	
            "metrics=s"  => \$metrics_name,
            "help+"      => \$help ); 

# Print help message
unless (!defined($help)) {
    print $helpMsg;
    exit(0);
}

# Read APs file
my @APs=();
open (FH, $antipatterns_name) or die" Unable to read file $antipatterns_name\n";
while (<FH>) {
  chop();
  @system = split /:0:::.\//, $_;
  $system[0] =~ s/smells-//;
  $system[0] =~ s/.txt//;
  @method = split /:::/, $system[1];
  push @APs, "$system[0]:$method[0]:$method[1]\n";
}
close(FH);

# Read metrics filenames
my @metrics_file=();
open (FH, $metrics_name) or die" Unable to read file $metrics_name\n";
while (<FH>) {
  chop();
  push @metrics_file, $_;
}
close(FH);

foreach my $file(@metrics_file) {
	tie my @array, 'Tie::File', $file or die $!;
	$file =~ s/-met-metr.csv//;

	foreach my $line(@array) {
	    # ...... line processing happens here .......
	    # ...... $line is automatically written to file if $line is changed .......
	    @metric = split /;/, $line;
	    $found = 0;

	    foreach my $ap(@APs) {
	    	@info = split /:/, $ap;

	    	if (($file eq $info[0]) and ($metric[0] eq $info[1]) and (index($info[2], $metric[1]) != -1)) {
	    		print "$file - $metric[0] - $metric[1] - $info[2]\n";

		    	if (index($info[2], "LongMethod") != -1) { 
		    		$found = 1;
		    		$line = "$line;1"; 
		    	} 
		    	else { $line = "$line;0"; }

		    	if (index($info[2], "LongParameterList") != -1) {
		    		$found = 1; 
		    		$line = "$line;1"; 
		    	}
		    	else { $line = "$line;0"; }

				if (index($info[2], "SpaghettiCode") != -1) { 
					$found = 1;
					$line = "$line;1"; 
				}
				else { $line = "$line;0"; }
	    	} 
	    }

	    if (!$found) {
	    	$line = "$line;0;0;0";
	    }
	}
}
















