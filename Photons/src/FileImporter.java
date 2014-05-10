import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;


public class FileImporter {
	
	// This is the source folder of the images to be imported. All subdirectories will be scanned.
	private Path pathToImportFrom;
	
	// The target folder of the import action. Imported files will be stored in the subdirectories of this folder.
	// The database storing file infos will also be located here
	private Path pathToImportTo;
	
	private String fileExtensionToImport;
	
	public FileImporter(String pathToImportFrom, String pathToImportTo, String fileExtensionToImport) {
		this.pathToImportFrom = Paths.get(pathToImportFrom);
		this.pathToImportTo = Paths.get(pathToImportTo);
		this.fileExtensionToImport = fileExtensionToImport;
	}
	
	public void Import() throws IOException {

		MyLogger.displayAndLogActionMessage(String.format("Importing files from [%s] to [%s]", this.pathToImportFrom, this.pathToImportTo));
		//System.out.println("Importing files from [" + this.pathToImportFrom.toString() + "] to [" + this.pathToImportTo.toString() + "]");

		// Next example with walkFileTree originates from http://docs.oracle.com/javase/7/docs/api/java/nio/file/FileVisitor.html
		Files.walkFileTree(this.pathToImportFrom, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
			new SimpleFileVisitor<Path>() {
//	             @Override
//	             public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
//	             {
//	            	 // TODO: implement. This version is only a copy from the original website
//	                 Path targetdir = pathToImportTo.resolve(pathToImportFrom.relativize(dir));
//	                 try {
//	                     Files.copy(dir, targetdir);
//	                 } catch (FileAlreadyExistsException e) {
//	                      if (!Files.isDirectory(targetdir))
//	                          throw e;
//	                 }
//	                 return FileVisitResult.CONTINUE;
//	             }
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
				{
					MyLogger.displayActionMessage(String.format("Importing file [%s]...", file));
					
					if (file.toString().toLowerCase().endsWith(fileExtensionToImport)) {
						FileToImportInfo fileToImportInfo = null;
						try {
							fileToImportInfo = new FileToImportInfo(file);
							FileImportedInfo fileImportedInfo = new FileImportedInfo(fileToImportInfo);
							
							Path targetFolder = Paths.get(pathToImportTo.toString(), fileImportedInfo.getSubfolder());
							Path targetPath = Paths.get(targetFolder.toString(), fileImportedInfo.getFileName());

							FileImportedInfo existingFileImportedInfo = null;
							existingFileImportedInfo = DatabaseUtil.getFileImportedInfo(pathToImportTo.toString(), fileImportedInfo.getOriginalHash(), fileImportedInfo.getOriginalLength());
							if (existingFileImportedInfo != null) {
								MyLogger.displayActionMessage(String.format("FileInfo in the database with the same hash and size already exists.", targetPath));
								MyLogger.displayActionMessage(String.format("MATCH: DB: [%s] Import: [%s]", Paths.get(pathToImportTo.toString(), existingFileImportedInfo.getSubfolder(), existingFileImportedInfo.getFileName()), file));
								if (existingFileImportedInfo.getImportEnabled()) {
									MyLogger.displayActionMessage(String.format("Reimporting..."));
								} else {
									return FileVisitResult.CONTINUE;
								}
							}
							
							if (Files.exists(targetPath)) {
								// TODO: maybe length and hash check would be nice here
								MyLogger.displayActionMessage(String.format("Target file already exists [%s]. Skipping copy.", targetPath));
							} else {
								MyLogger.displayActionMessage(String.format("Copying file from [%s] to [%s]", file, targetPath));
								if (Files.exists(targetFolder)) {
									MyLogger.displayActionMessage(String.format("Target folder already exists [%s].", targetFolder));
								} else {
									Files.createDirectories(targetFolder);
								}
								
								Files.copy(file, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
								
								// Verification of file copy:
								if (!fileImportedInfo.getOriginalHash().equals(FileUtil.getFileContentHash(targetPath.toString()))) {
									MyLogger.displayAndLogActionMessage(String.format("ERROR: error during copying file from: [%s] to [%s]. File content hash difference.", file, targetPath));
									return FileVisitResult.CONTINUE;
								}
								
								if (fileImportedInfo.getOriginalLength() != Files.size(targetPath)) {
									MyLogger.displayAndLogActionMessage(String.format("ERROR: error during copying file from: [%s] to [%s]. File length difference.", file, targetPath));
									return FileVisitResult.CONTINUE;
								}
								
								DatabaseUtil.saveFileImportedInfo(pathToImportTo.toString(), fileImportedInfo);
								existingFileImportedInfo = DatabaseUtil.getFileImportedInfo(pathToImportTo.toString(), fileImportedInfo.getOriginalHash(), fileImportedInfo.getOriginalLength());
								if (existingFileImportedInfo == null) {
									// TODO: how to retry?
									MyLogger.displayAndLogActionMessage(String.format("ERROR: error during database insert."));
									return FileVisitResult.CONTINUE;
								}
								
								MyLogger.displayAndLogActionMessage(String.format("File imported from: [%s] to [%s].", file, targetPath));
							}
						} catch (Exception e) {
							MyLogger.displayAndLogActionMessage(String.format("ERROR: Failed to import file [%s].", file));
							e.printStackTrace();
						}
						
					} else {
						System.out.println(String.format("Ignoring file because of file type mismatch [%s].", file));
					}

					return FileVisitResult.CONTINUE;
				}
			});
		}
}
