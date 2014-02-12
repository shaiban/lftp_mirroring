import groovy.io.FileType


  /*
   * Utility to scan for new builds on ftp and download them locally
   *
   *
   *
   *
   * 2013 ykachube
   */

def DOWNLOAD_TREAD_WAIT = 5*60*1000 //5 min

def final MAX_THREADS = 5

current_threads=0

def running = true


def ROOT_DIR="e:\\_mirror"

def final EXEC_TIMEOUT = 24*60*60*1000 // millisec,  kill exec after 24 hour

def final UZIP_THREAD_LIMIT= 5 //no more than 5 uzipping threads



def run_this = {List cmd ->
	def out = new StringBuilder()
	def err = new StringBuilder()
	def proc = cmd.execute()
	proc.consumeProcessOutput(out, err);
	proc.waitForOrKill(EXEC_TIMEOUT)
	if (out) println "out:\n$out"
	if (err) println "err:\n$err"
		}

println ( " Daorrim ROAD to EPAM mirroring script " + Calendar.getInstance().getTime())

    
	
	while(running==true){
	if (current_threads<MAX_THREADS)
		{
		//run winscp download to temp dir  -delete every x min
		cmd=[ROOT_DIR+"\\winscp\\winscp.com","/script=ftp_script"]
		run_this(cmd)
		
		//scan for temp files - unpack and delete all
		
		File temp_dir= new File(ROOT_DIR+"\\temp")
		ant = new AntBuilder()
		temp_dir.eachFile {
			
			if (it.size()<=0) {println "bad file" + it.absolutePath }
			else {

			        File zfile = it	  
				//Thread.start 
			      //{
				   current_threads++;
				    
				   println ">" + Thread.currentThread().getName() + " start for " + zfile.name
				//ant.unzip(src:zfile.absolutePath, dest:ROOT_DIR+"\\releases")
				unzipcmd=["7-zip\\7z","x","-oreleases",zfile.absolutePath,"-y"]
                             println (unzipcmd)
                                run_this(unzipcmd)
			    zfile.delete()
                            println ">" + Thread.currentThread().getName() + " end"
			current_threads--;	
                         //}
			}
                        			
			
		} 
		
		} else println "MAX_THREADS limit reached"
		sleep (DOWNLOAD_TREAD_WAIT)
//cleanup older builds
	

	}
	



	

	
	
	
