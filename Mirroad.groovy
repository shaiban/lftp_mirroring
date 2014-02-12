/*
 * Utility to scan for new builds and upload them to ftp site
 *
 * Only detects new files - no actions on removed files.
 *
 * Renamed files  considered as new - need to fix that (see jpathwatch) - skip all not NEW? RCN||REL||SAVE?
 *
 * uses lftp for hftp 
 *
 * 2013 ykachube
 */


  println ( " Mirroad mirroring script " + Calendar.getInstance().getTime())
  


  //remote side
  def final DEFAULT_ROOT_PATH = "d:\\files"
  
  def final DEFAULT_FTP_FOLDER="_mirror"
  
  def final SCAN_INTERVAL_SEC = 5
  
  def final UPLOAD_LOOP_INTERVAL_SEC= 10
  
  def final MAX_UPLOAD_THREADS= 2
  def final EXEC_TIMEOUT = 2*60*60*1000 // millisec,  kill after 2 hour
  
  def final MAX_RETRY_COUNT=10

  def SECONDS_IN_QUEUE= 10
  
  def running = true
  //init - scan initial tree

      def exceptions=["RCN","REL","SAVE"]


	 def check_exceptions = {String filename ->
	
		flag=true
        exceptions.each({if(filename.contains(it)){flag=false}})
		return (flag)
	 }
  
  def rootPath=DEFAULT_ROOT_PATH
  
  def filesMap = new HashMap()
  
  def uploadQueue = new LinkedList ([])
  
	 ant = new AntBuilder()
  
  def run_this = {List cmd, String error_token ->
	  def out = new StringBuilder()
	  def err = new StringBuilder()
	  def proc = cmd.execute()
	  proc.consumeProcessOutput(out, err);
	  proc.waitForOrKill(EXEC_TIMEOUT)
	  if (out) println ">" + Calendar.getInstance().getTime()+ "out:\n$out"
	  if (err) println ">" + Calendar.getInstance().getTime() + "err:\n$err"
			if (out.toString().contains(error_token)) return 1
			if (err.toString().contains(error_token)) return 1
	  return(0)
  }



  
  class FileInfo {
	  //String path;
	  String last_modified;
	  int size;
  
  }
  
  class UploadThis {
	  String path;
	  Calendar queued_at;
	  FileInfo file_info;
	  int upload_time;
  }
  
  class Stats
  {
	  List uploaded_files
	  int queue_length
	  List files_in_progress
	  Date stat_start
	  Date stat_end
	  
  }

  
  Runtime.getRuntime().addShutdownHook(new Thread() {
	  public void run() {
		  
		 println ">" + Calendar.getInstance().getTime()+ "Terminating"
		 running = false
	   }
   });
  
  
  /**
   * The current implementation only support one optional command line argument, which is to
   * specify a root directory.
   */
  if (args.size() > 1) {
	println("Usage: " + this.metaClass.theClass.name + ".groovy <root dir>")
	println()
	System.exit(1)
  } else if (args.size() == 1) {
	rootPath = args[0]
  }
  
  def rootDir = new File(rootPath)
  if (!rootDir.exists()) {
	println rootPath + " does not exist. "
	System.exit(2)
  } else if (!rootDir.isDirectory()) {
	println rootPath + " is not a directory. "
	System.exit(3)
  }
  
	 //init file list
  def mScanDir
  mScanDir =
  {
	  //println(dir.name)
	  it.eachFile ({filesMap.put(it.absolutePath, new FileInfo(last_modified:it.lastModified(), size:it.length() ) )})
	  it.eachDir({mScanDir(it)})
  }
  
  //scan for changes
  def mScanDirForChanges
   mScanDirForChanges =
  {
	  //println(dir.name)
	  it.eachFile (
		   { if (!filesMap.containsKey(it.absolutePath)&&!(it.isDirectory()))
			   {
				   println "new file detected! " + it.absolutePath
				   path = it.absolutePath
				   FileInfo file_info = new FileInfo(last_modified:it.lastModified(), size:it.length())
				   filesMap.put(path,file_info )
				   if (check_exceptions(it.absolutePath))
				   {
					   Calendar upload_at = Calendar.getInstance()
				   
				   upload_at.add(Calendar.SECOND,SECONDS_IN_QUEUE) //debug
				   
				   UploadThis one = new UploadThis(path:path, queued_at:upload_at, file_info:file_info);
				   uploadQueue.add(one) //add new file to upload queue
				   } else {println "exception list - no upload for " + it.absolutePath}
				}
		   }
	  )
	  it.eachDir({mScanDirForChanges(it)})
  }
  

  println ("Scanning")
  rootDir.eachDir({mScanDir(it)})
  
  println filesMap.size() + " entries found initially"
  
  
  
  println ("Starting scan thread")
  def scanThread = Thread.start {
	  counter =0
	  while ((runnig=true)){
		  //just scan for changes every 5 min
		  rootDir.eachDir({mScanDirForChanges(it)})
		  sleep(SCAN_INTERVAL_SEC*1000)
		  //break;
		  counter++;
	  }
  println "Scan thread end"
  }
  
  println ("Starting upload thread")
  def uploadThread = Thread.start {
	  counter = 0
	  uploads = 0
	  while ((running=true)){
		  //scan queue
		  
		  
		  while (uploadQueue.peek())
		  {
			  // println uploadQueue.peek().path
			  Calendar upload_at=((UploadThis)uploadQueue.peek()).queued_at
			  
			  
			  //if ready to upload after x mins in queue
			  if ( Calendar.getInstance().after(upload_at))
				  {
				  
					  
					  //upload if upload < UPLOAD_THREADS
					  //uploadQueue.poll()
					  //in new thread
					  
					  if (uploads< MAX_UPLOAD_THREADS)
					  {
						  uploads++;
						  UploadThis file=uploadQueue.poll()
						  println "upload queue size " + uploadQueue.size()
						  Thread.start {
							  
								  //zip!
										// ugly hack to fix lftp unable to create folders
							  
							  String filename=file.path.substring(file.path.lastIndexOf(File.separator)+1)
							  String internal_path=file.path.substring(rootDir.absolutePath.length()+1,file.path.lastIndexOf(File.separator))
							   

							  ant.zip(destfile:"/tmp/${filename}.zip",
								  basedir:"${rootDir.absolutePath}",
								  includes:"${internal_path}/${filename}",
								  update:"false" )
							  
//cmd="export ftp_proxy=http://proxy.example.com:80 ; lftp -c 'open remote  ;  put /tmp/${filename}.zip -o /${DEFAULT_FTP_FOLDER}/${filename}.zip ; dir'"
cmd=["/bin/sh", "-c", "export ftp_proxy=http://proxy.example.com:80 ;  lftp -c 'open remote  ;  put /tmp/${filename}.zip -o  /${DEFAULT_FTP_FOLDER}/${filename}.zip  '"]


							  println Thread.currentThread().getName() + cmd
							  
							  retry_upload=1
							  retry_upload_count=0
							  while ((retry_upload)&&(retry_upload_count<MAX_RETRY_COUNT))
							  {

								retry_upload_count++
							   println "Upload tier " + retry_upload_count
								retry_upload=run_this(cmd,"badrequest")
							  }
                                 new File("/tmp/${filename}.zip").delete()
							  
							  println Thread.currentThread().getName() + " uploaded ok"
							  uploads--;
						  }
						  
					  }
                      else {print ">wt" ; break;} //max threads reached
					

					  
					  //verify
					  
					  //if fail - notify via email, keep trying
					  
					  //else remove file from queue
					  
					  
					  
					  
				  }
				  else {println "waiting queue "; break;} //first element to wait - sleep
			  
		  }
		  
		  
		  sleep(UPLOAD_LOOP_INTERVAL_SEC*1000)
		  
		  counter ++
		  
					  
	  }
	  println "Upload thread end"
	  
	  }
	  
	  

  
			  
  println ("Init complete.")
