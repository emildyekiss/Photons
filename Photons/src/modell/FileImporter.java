package modell;

import java.io.IOException;
//import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

//import java.util.EnumSet;

import common.FileUtil;
import common.MyLogger;


public class FileImporter {
	// This is the source folder of the images to be imported. All subdirectories will be scanned.
	private Path pathToImportFrom;
	
	// The target folder of the import action. Imported files will be stored in the subdirectories of this folder.
	// The database storing file infos will also be located here
	private Path pathToImportTo;
	
	private String fileExtensionToImport;
	
	private FileInfoDatabase fileInfoDatabase;
	
	public FileImporter(String pathToImportFrom, String pathToImportTo, String fileExtensionToImport) {
		this.pathToImportFrom = Paths.get(pathToImportFrom);
		this.pathToImportTo = Paths.get(pathToImportTo);
		this.fileExtensionToImport = fileExtensionToImport;
		
		this.fileInfoDatabase = new FileInfoDatabase(this.pathToImportTo);
	}
	
	public void Import() throws IOException {
		MyLogger.displayAndLogActionMessage("Importing files from [%s] to [%s]", this.pathToImportFrom, this.pathToImportTo);
		this.fileInfoDatabase.openOrCreateDatabase();
		
		// Next example with walkFileTree originates from http://docs.oracle.com/javase/7/docs/api/java/nio/file/FileVisitor.html
		//Files.walkFileTree(this.pathToImportFrom, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
		Files.walkFileTree(this.pathToImportFrom,
			new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
				{
					FileImporter.this.visitFile(file);
					return FileVisitResult.CONTINUE;
				}

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
					MyLogger.displayAndLogActionMessage("ERROR: Failed to import file [%s].", file);
					MyLogger.displayAndLogException(e);
                    return FileVisitResult.SKIP_SUBTREE;
                }
			});
		}

	private void visitFile(Path file) {
		if (!file.toString().toLowerCase().endsWith(fileExtensionToImport)) {
			MyLogger.displayActionMessage("Ignoring file because of file type mismatch [%s].", file);
			return;
		}

		MyLogger.displayAndLogActionMessage("Importing file [%s]...", file);
		try {
			FileToImportInfo fileToImportInfo = new FileToImportInfo(file);
			FileImportedInfo fileImportedInfo = new FileImportedInfo(fileToImportInfo);
			
			Path targetFolder = Paths.get(pathToImportTo.toString(), fileImportedInfo.getSubfolder());
			Path targetPath = Paths.get(targetFolder.toString(), fileImportedInfo.getFileName());

			FileImportedInfo existingFileImportedInfo = null;
			existingFileImportedInfo = fileInfoDatabase.getFileImportedInfo(pathToImportTo.toString(), fileImportedInfo.getOriginalHash(), fileImportedInfo.getOriginalLength());
			if (existingFileImportedInfo != null) {
				MyLogger.displayActionMessage("FileInfo in the database with the same hash and size already exists.", targetPath);
				MyLogger.displayAndLogActionMessage("MATCH: DB: [%s] Import: [%s]", Paths.get(pathToImportTo.toString(), existingFileImportedInfo.getSubfolder(), existingFileImportedInfo.getFileName()), file);
				if (!existingFileImportedInfo.getImportEnabled()) {
					MyLogger.displayAndLogActionMessage("Skipping...");
					return;
				}
				MyLogger.displayAndLogActionMessage("Reimporting...");
			}
			
			if (Files.exists(targetPath)) {
				// TODO: maybe length and hash check would be nice here
				// This case can happen. E.g. if the picture was saved and resized
				// to another location with the same name.
				// But at least a warning should be logged to be able to check later.
				Path oldTargetPath = targetPath;
				targetPath = FileUtil.getAlternateFileName(oldTargetPath);
				MyLogger.displayAndLogActionMessage("WARNING: Target file already exists [%s]. Generated new file name: [%s].", oldTargetPath, targetPath);
				
				fileImportedInfo.setFileName(targetPath.getFileName().toString());
			}
			
			MyLogger.displayActionMessage("Copying file from [%s] to [%s]", file, targetPath);
			if (!Files.exists(targetFolder)) {
				Files.createDirectories(targetFolder);
			}
			Files.copy(file, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
			
			// Verification of file copy:
			if (fileImportedInfo.getOriginalLength() != Files.size(targetPath)) {
				MyLogger.displayAndLogActionMessage("ERROR: error during copying file from: [%s] to [%s]. File length difference.", file, targetPath);
				return;
			}
			if (!fileImportedInfo.getOriginalHash().equals(FileUtil.getFileContentHash(targetPath.toString()))) {
				MyLogger.displayAndLogActionMessage("ERROR: error during copying file from: [%s] to [%s]. File content hash difference.", file, targetPath);
				return;
			}
			
			fileInfoDatabase.saveFileImportedInfo(pathToImportTo.toString(), fileImportedInfo);
			FileImportedInfo createdFileImportedInfo = fileInfoDatabase.getFileImportedInfo(pathToImportTo.toString(), fileImportedInfo.getOriginalHash(), fileImportedInfo.getOriginalLength());
			if (createdFileImportedInfo == null) {
				// TODO: how to retry?
				MyLogger.displayAndLogActionMessage("ERROR: error during database insert.");
				return;
			}
			
			MyLogger.displayAndLogActionMessage("File imported from: [%s] to [%s].", file, targetPath);
		} catch (Exception e) {
			MyLogger.displayAndLogActionMessage("ERROR: Failed to import file [%s].", file);
			MyLogger.displayAndLogException(e);
		}
	}
}
