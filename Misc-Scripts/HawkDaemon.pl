#!/usr/bin/perl

use strict;
use warnings;
use Getopt::Long;
use Proc::Daemon;
use Cwd;
use File::Spec::Functions;
use File::Path qw(make_path remove_tree);

use constant LIMIT => 15; # max number of spanned processes

my $pf = catfile(getcwd(), 'pidfile.pid');
my $daemon = Proc::Daemon->new(
	pid_file => $pf,
	work_dir => getcwd()
);
# are you running?  Returns 0 if not.
my $pid = $daemon->Status($pf);
my $daemonize = 1;

my $tasks=(); # file with list of spanning tasks
my $runid=localtime(); # id for this run -default localtime
my @jobs =(); # actual list of jobs form file tasks
my $store_path="runs"; # default store path !

$runid =~ s/\s+/ /g;
$runid =~ s/ /-/g;
$runid =~ s/:/-/g;
#
# this is a simplified daemon and thus the order of options is important
# when you start -tasks  MUST me the first parameter or the child will not see it
#
# starts as:
#
# ./HawkDaemon.pl -tasks aaa   -start
#

GetOptions(
	   'daemon!' => \$daemonize,
	   "tasks=s" => \$tasks,
	   "runid=s" => \$runid,
	   "start" => \&run,
	   "status" => \&status,
	   "stop" => \&stop
	  );

sub stop {
        if ($pid) {
	        print "Stopping pid $pid...\n";
	        if ($daemon->Kill_Daemon($pf)) {
		        print "Successfully stopped.\n";
	        } else {
		        print "Could not find $pid.  Was it running?\n";
	        }
         } else {
                print "Not running, nothing to stop.\n";
         }
}

sub status {
	if ($pid) {
		print "Running with pid $pid.\n";
	} else {
		print "Not running.\n";
	}
}

sub run {
	if (!$pid) {
		print "Starting...\n";
		if ($daemonize) {
			# when Init happens, everything under it runs in the child process.
			# this is important when dealing with file handles, due to the fact
			# Proc::Daemon shuts down all open file handles when Init happens.
			# Keep this in mind when laying out your program, particularly if
			# you use filehandles.
			$daemon->Init;
		      }

		# input tasks if any ...
		
		if (defined ($tasks)){
		  open (my $FT, $tasks);
		  @jobs=<$FT>;
		  close($FT);
		  chop (@jobs);
		}
		my ($sys, $fold)=0;
		
		while (1) {
		  open(my $FH, '>>', catfile(getcwd(), "log.txt"));
		  # any code you want your daemon to run here.
		  # this example writes to a filehandle every 5 seconds.
		  print $FH "Logging at " . time() . "\n\n";
		 

		  open (my $PIPE, "screen -ls |") or print $FH "Unable to pipe\n;";
		  my @lines = <$PIPE>;
		  close ($PIPE);
		  my $processes = $#lines - 1;

		  if ($processes < LIMIT){
		    if ($#jobs>=0){
		      my $task = shift @jobs;
		      my ($neg,$pos) = split(":",$task);
		      if ($pos=~/(\d+)-train-([\w\d\.]+)-.*/){
			$fold = $1;
			$sys = $2;
			$store_path="runs/". $runid ."/" . $sys ."/". $fold."/";
			my $dir = $store_path;
			#print $FH "RunId $runid Start $sys at $fold\n";
			#print $FH "StorePath >>$store_path<<\n";
			if ( ! -d $dir){
			   print $FH "\nCreateDir: >>$dir<\n";
			   make_path ($dir );
			}
			my $cmd="./train.py --where $store_path --enable_word_embeddings true --num_epochs 6 --dev_sample_percentage 0.01 --positive_train=$pos --negative_train=$neg";
			system("screen -d -m  ./train.py  --where $store_path --enable_word_embeddings true --num_epochs 6 --dev_sample_percentage 0.01 --positive_train=$pos --negative_train=$neg");
			print $FH "$cmd\n";
			
		      }else{
			print $FH "Wrong line format >> $task<<\n";
			exit(1);
		      }
		      print $FH "\nTask: $task\n";
		    }
		    close $FH;
		    sleep 20;
		  }else{
		      print $FH "ZZZZZ .... Sleep 10 minutes...\n";
		      close $FH;
		      sleep 900;
		  }
		  
		}
	} else {
		print "Already Running with pid $pid\n";
	}
}
