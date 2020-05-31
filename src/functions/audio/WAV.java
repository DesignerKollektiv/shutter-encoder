/*******************************************************************************************
* Copyright (C) 2020 PACIFICO PAUL
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation; either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License along
* with this program; if not, write to the Free Software Foundation, Inc.,
* 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
* 
********************************************************************************************/

package functions.audio;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import javax.swing.JOptionPane;

import application.Ftp;
import application.Settings;
import application.Shutter;
import application.Utils;
import application.Wetransfer;
import library.FFMPEG;
import library.FFPROBE;

public class WAV extends Shutter {
	
	
	private static int complete;
	
	public static void main() {
		
		Thread thread = new Thread(new Runnable(){			
			@Override
			public void run() {
				if (scanIsRunning == false)
					complete = 0;
				
				lblTermine.setText(Utils.fichiersTermines(complete));

				for (int i = 0 ; i < liste.getSize() ; i++)
				{
					File file = new File(liste.getElementAt(i));
					
					//SCANNING
		            if (Shutter.scanIsRunning)
		            {
		            	file = Utils.scanFolder(liste.getElementAt(i));
		            	if (file != null)
		            		btnStart.setEnabled(true);
		            	else
		            		break;
		            	Shutter.progressBar1.setIndeterminate(false);		
		            }
		            else if (Settings.btnWaitFileComplete.isSelected())
		            {
						progressBar1.setIndeterminate(true);
						lblEncodageEnCours.setForeground(Color.LIGHT_GRAY);
						lblEncodageEnCours.setText(file.getName());
						tempsRestant.setVisible(false);
						btnStart.setEnabled(false);
						btnAnnuler.setEnabled(true);
						comboFonctions.setEnabled(false);
						
						long fileSize = 0;
						do {
							fileSize = file.length();
							try {
								Thread.sleep(3000);
							} catch (InterruptedException e) {} // Permet d'attendre la nouvelle valeur de la copie
						} while (fileSize != file.length() && cancelled == false);

						// pour Windows
						while (file.renameTo(file) == false && cancelled == false) {
							if (file.exists() == false) // Dans le cas où on annule la copie en cours
								break;
							try {
								Thread.sleep(10);
							} catch (InterruptedException e) {
							}
						}
						
						if (cancelled)
						{
							progressBar1.setIndeterminate(false);
							lblEncodageEnCours.setText(language.getProperty("lblEncodageEnCours"));
							btnStart.setEnabled(true);
							btnAnnuler.setEnabled(false);
							comboFonctions.setEnabled(true);
							break;
						}
						
						progressBar1.setIndeterminate(false);
						btnAnnuler.setEnabled(false);
		            }
		           //SCANNING
		            
					try {
					String fichier = file.getName();					
					final String extension =  fichier.substring(fichier.lastIndexOf("."));	
					lblEncodageEnCours.setText(fichier);
					
					//Analyse des données
					if (analyse(file) == false)
						continue;		
					
					String concat = "";
					//Traitement de la file en Bout à bout
					if (Settings.btnSetBab.isSelected())
					{
						file = setBAB(fichier, extension);	
						if (caseActiverSequence.isSelected() == false)
						concat = " -safe 0 -f concat";
					}
					
					//Dossier de sortie
					String sortie = setSortie("", file);
					
					//Fichier de sortie
					String nomExtension;
					if (Settings.btnExtension.isSelected())
						nomExtension = Settings.txtExtension.getText();
					else		
						nomExtension =  "_MIX";
					
					String sortieFichier;
					if (caseMixAudio.isSelected())						
						sortieFichier =  sortie + "/" + fichier.replace(extension, nomExtension + ".wav");
					else
						sortieFichier =  sortie + "/" + fichier.replace(extension, ".wav");
					
		           	//Audio
					String audio = setAudio("");						
		           	
					//InOut		
					FFMPEG.fonctionInOut();
					
					//Si le fichier existe
					File fileOut = new File(sortieFichier);
					if(fileOut.exists() && caseSplitAudio.isSelected() == false)
					{						
						if (caseMixAudio.isSelected())						
							fileOut = Utils.fileReplacement(sortie, fichier, extension, nomExtension + "_", ".wav");
						else
							fileOut = Utils.fileReplacement(sortie, fichier, extension, "_", ".wav");
						if (fileOut == null)
							continue;	
					}
									
					//Envoi de la commande					
					if (caseSplitAudio.isSelected()) //Permet de créer la boucle de chaque canal audio						
						splitAudio(fichier, extension, file, sortie);
					else
					{
						String cmd = " " + audio + "-vn -y ";
						FFMPEG.run(FFMPEG.inPoint + concat + " -i " + '"' + file.toString() + '"' + FFMPEG.postInPoint + FFMPEG.outPoint + cmd + '"'  + fileOut + '"');
					}								
					
					//Attente de la fin de FFMPEG
					do
						Thread.sleep(100);
					while(FFMPEG.runProcess.isAlive());

					if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false && caseSplitAudio.isSelected() == false
					|| FFMPEG.saveCode == false && Settings.btnSetBab.isSelected())
					{
						if (actionsDeFin(fichier, fileOut, sortie))
							break;
					}
					
					} catch (InterruptedException e) {
						FFMPEG.error  = true;
					}//End Try
				}//End For	

				if (btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false)
					FinDeFonction();
			}//run
			
		});
		thread.start();
		
    }//main

	protected static File setBAB(String fichier, String extension) {
		
		String sortie =  new File(liste.getElementAt(0)).getParent();
		
		if (caseChangeFolder1.isSelected())
			sortie = lblDestination1.getText();
			
		File listeBAB = new File(sortie.replace("\\", "/") + "/" + fichier.replace(extension, ".txt")); 
		
		try {			
			int dureeTotale = 0;
			frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));			
			PrintWriter writer = new PrintWriter(listeBAB, "UTF-8");      
			
			for (int i = 0 ; i < liste.getSize() ; i++)
			{				
				//Scanning
				if (Settings.btnWaitFileComplete.isSelected())
	            {
					File file = new File(liste.getElementAt(i));
					
					progressBar1.setIndeterminate(true);
					lblEncodageEnCours.setForeground(Color.LIGHT_GRAY);
					lblEncodageEnCours.setText(file.getName());
					tempsRestant.setVisible(false);
					btnStart.setEnabled(false);
					btnAnnuler.setEnabled(true);
					comboFonctions.setEnabled(false);
					
					long fileSize = 0;
					do {
						fileSize = file.length();
						try {
							Thread.sleep(3000);
						} catch (InterruptedException e) {} // Permet d'attendre la nouvelle valeur de la copie
					} while (fileSize != file.length() && cancelled == false);

					// pour Windows
					while (file.renameTo(file) == false && cancelled == false) {
						if (file.exists() == false) // Dans le cas où on annule la copie en cours
							break;
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
						}
					}
					
					if (cancelled)
					{
						progressBar1.setIndeterminate(false);
						lblEncodageEnCours.setText(language.getProperty("lblEncodageEnCours"));
						btnStart.setEnabled(true);
						btnAnnuler.setEnabled(false);
						comboFonctions.setEnabled(true);
						break;
					}
					
					progressBar1.setIndeterminate(false);
					btnAnnuler.setEnabled(false);
	            }
				//Scanning
				
				FFPROBE.Data(liste.getElementAt(i));
				do {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e1) {}
				} while (FFPROBE.isRunning == true);
				dureeTotale += FFPROBE.dureeTotale;
				
				writer.println("file '" + liste.getElementAt(i) + "'");
			}				
			writer.close();
						
			frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			progressBar1.setMaximum((int) (dureeTotale / 1000));
			FFPROBE.dureeTotale = progressBar1.getMaximum();
			FFMPEG.dureeTotale = progressBar1.getMaximum();
			
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			FFMPEG.error  = true;
			if (listeBAB.exists())
				listeBAB.delete();
		}//End Try
		
		return listeBAB;
	}

	private static String setAudio(String audio) {
		
		String audioSpeed = "";
		if (caseConvertAudioFramerate.isSelected())     
        {
        	float AudioFPSIn = Float.parseFloat((comboAudioIn.getSelectedItem().toString()).replace(",", "."));
        	float AudioFPSOut = Float.parseFloat((comboAudioOut.getSelectedItem().toString()).replace(",", "."));
        	float value = (float) (AudioFPSOut / AudioFPSIn);
        	audioSpeed = ",atempo=" + value;	
        }
		
		if (caseMixAudio.isSelected())						
		{
			for (int n = 1 ; n < liste.size() ; n++)
			{
				audio += "-i " + '"' + liste.elementAt(n) + '"' + " ";
			}
			
			if (FFPROBE.stereo)
				audio += "-filter_complex amerge=inputs=" + liste.size() + audioSpeed + " -ac 2 ";
			else
			{
				audio += "-filter_complex " + '"';
				String left = "";
				int cl = 0;
				String right = "";
				int cr = 0;
				for (int n = 0 ; n < liste.size() ; n++)
				{
					if (n % 2 == 0) //les chiffres paires à gauche
					{
						left += "[" + n + ":0]";
						cl++;
					}
					else			//les chiffres impaires à droite
					{
						right += "[" + n + ":0]";
						cr++;
					}
				}
				audio += left + "amerge=inputs=" + cl + ",channelmap=map=FL[left];" + right + "amerge=inputs=" + cr + ",channelmap=map=FR[right];";
						
				audio += "[left][right]amerge=inputs=2" + audioSpeed + "[out]" + '"' + " -map " + '"' + "[out]" + '"' + " -ac 2 ";
			}							
		}
		else if (FFPROBE.stereo)
		{
			audio = "-map a:0 ";
			if (caseConvertAudioFramerate.isSelected())     
				audio += audioSpeed.replace(",", " -filter_complex ") + " ";
		}
		else if (FFPROBE.channels > 1)
	    	audio = "-filter_complex " + '"' + "[0:a:0][0:a:1]amerge=inputs=2" + audioSpeed + "[a]" + '"' + " -map " + '"' + "[a]" + '"' + " ";	
		else //Fichier Mono
		{
			if (caseConvertAudioFramerate.isSelected())     
				audio += audioSpeed.replace(",", " -filter_complex ") + " ";
		}
			
		//Quantization
		if (comboFilter.getSelectedItem().toString().contains("Float"))
			audio += "-acodec pcm_f" + comboFilter.getSelectedItem().toString().replace(" Float", "") + "le ";	
		else
			audio += "-acodec pcm_s" + comboFilter.getSelectedItem().toString().replace(" Bits", "") + "le ";							
		
		return audio;
	}
	
	private static void splitAudio(String fichier, String extension, File file, String sortie) throws InterruptedException {
		
		String audioSpeed = "";
		if (caseConvertAudioFramerate.isSelected())     
        {
        	float AudioFPSIn = Float.parseFloat((comboAudioIn.getSelectedItem().toString()).replace(",", "."));
        	float AudioFPSOut = Float.parseFloat((comboAudioOut.getSelectedItem().toString()).replace(",", "."));
        	float value = (float) (AudioFPSOut / AudioFPSIn);
        	audioSpeed = ",atempo=" + value;	
        }
		
		if (FFPROBE.channels == 1 && lblSplit.getText().equals("Mono"))
		{			
			
			for (int i = 1 ; i < 3; i ++)
			{
				//Si le fichier existe
				String yesno = " -y ";
				File fileOut = new File(sortie + "/" + fichier.replace(extension, "_Audio_" + i + ".wav"));
				if(fileOut.exists())
				{										
					fileOut = Utils.fileReplacement(sortie, fichier, extension, "_Audio_" + i + "_", ".wav");
					if (fileOut == null)
						yesno = " -n ";	
				}
				
				String cmd = " -filter_complex " + '"' + "[a:0]pan=1c|c0=c" + (i - 1) + audioSpeed + "[a" + (i - 1) + "]" + '"' + " -map " + '"'+ "[a" + (i - 1) + "]" + '"' + " -acodec pcm_s" + comboFilter.getSelectedItem().toString().replace(" Bits", "") + "le -vn" + yesno;
				FFMPEG.run(FFMPEG.inPoint + " -i " + '"' + file.toString() + '"' + FFMPEG.postInPoint + FFMPEG.outPoint + cmd + '"'  + fileOut + '"');	
				
				do
					Thread.sleep(100);
				while(FFMPEG.runProcess.isAlive());	
				
				if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false)
				{
					if (actionsDeFin(fichier, fileOut, sortie))
						break;
				}
			}
			
		}
		else if (FFPROBE.channels == 1 && lblSplit.getText().equals(Shutter.language.getProperty("stereo"))) //Si le fichier est stéréo et demandé en stéréo on ne split rien
		{
			JOptionPane.showMessageDialog(Shutter.frame, Shutter.language.getProperty("theFile") + " " + fichier + " " + Shutter.language.getProperty("isAlreadyStereo"), Shutter.language.getProperty("cantSplitAudio"), JOptionPane.ERROR_MESSAGE);
		}
		else if (FFPROBE.channels > 1 && lblSplit.getText().equals("Mono"))
		{
			for (int i = 1 ; i < FFPROBE.channels + 1; i ++)
			{
				//Si le fichier existe
				String yesno = " -y ";
				File fileOut = new File(sortie + "/" + fichier.replace(extension, "_Audio_" + i + ".wav"));
				if(fileOut.exists())
				{										
					fileOut = Utils.fileReplacement(sortie, fichier, extension, "_Audio_" + i + "_", ".wav");
					if (fileOut == null)
						yesno = " -n ";	
				}
				
				if (caseConvertAudioFramerate.isSelected())     
					audioSpeed = audioSpeed.replace(",", " -filter_complex ");
				
				String cmd = audioSpeed + " -map a:" + (i - 1) + " -acodec pcm_s" + comboFilter.getSelectedItem().toString().replace(" Bits", "") + "le -vn" + yesno;
				FFMPEG.run(FFMPEG.inPoint + " -i " + '"' + file.toString() + '"' + FFMPEG.postInPoint + FFMPEG.outPoint + cmd + '"'  + fileOut + '"');	
				
				do
					Thread.sleep(100);
				while(FFMPEG.runProcess.isAlive());	
				
				if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false)
				{
					if (actionsDeFin(fichier, fileOut, sortie))
						break;
				}
			}
		}
		else if (FFPROBE.channels > 1 && lblSplit.getText().equals(Shutter.language.getProperty("stereo")))
		{
			
			int number = 1;
			
			for (int i = 1 ; i < FFPROBE.channels + 1; i +=2)
			{
				
				//Si le fichier existe
				String yesno = " -y ";
				File fileOut = new File(sortie + "/" + fichier.replace(extension, "_Audio_" + number + ".wav"));
				if(fileOut.exists())
				{										
					fileOut = Utils.fileReplacement(sortie, fichier, extension, "_Audio_" + number + "_", ".wav");
					if (fileOut == null)
						yesno = " -n ";	
				}
				
				String cmd = " -filter_complex " + '"' + "[0:a:" + (i - 1) + "][0:a:" + i + "]amerge=inputs=2" + audioSpeed + "[a]" + '"' + " -map " + '"' + "[a]" + '"' + " -acodec pcm_s" + comboFilter.getSelectedItem().toString().replace(" Bits", "") + "le -vn" + yesno;
				FFMPEG.run(FFMPEG.inPoint + " -i " + '"' + file.toString() + '"' + FFMPEG.postInPoint + FFMPEG.outPoint + cmd + '"'  + fileOut + '"');	
				
				do
					Thread.sleep(100);
				while(FFMPEG.runProcess.isAlive());	
				
				if (FFMPEG.saveCode == false && btnStart.getText().equals(Shutter.language.getProperty("btnAddToRender")) == false)
				{
					if (actionsDeFin(fichier, fileOut, sortie))
						break;
				}
				
				number ++;
			}
		}
		else
		{
			FFMPEG.errorList.append(fichier);
		    FFMPEG.errorList.append(System.lineSeparator());
		}
		
	}
	
	protected static boolean analyse(File file) throws InterruptedException {
		 FFPROBE.Data(file.toString());

		 do
			Thread.sleep(100);
		 while (FFPROBE.isRunning);
		 					 
		 if (errorAnalyse(file.toString()))
			return false;
		 
		 return true;
	}
	
	protected static String setSortie(String sortie, File file) {
		if (caseChangeFolder1.isSelected())
			sortie = lblDestination1.getText();
		else
		{
			sortie =  file.getParent();
			lblDestination1.setText(sortie);
		}
		
		return sortie;
	}
	
	private static boolean errorAnalyse (String fichier)
	{
		 if (FFMPEG.error)
		 {
				FFMPEG.errorList.append(fichier);
			    FFMPEG.errorList.append(System.lineSeparator());
				return true;
		 }
		 return false;
	}
	
	private static boolean actionsDeFin(String fichier, File fileOut, String sortie) {
		//Erreurs
		if (FFMPEG.error || fileOut.length() == 0)
		{
			FFMPEG.errorList.append(fichier);
		    FFMPEG.errorList.append(System.lineSeparator());
			try {
				fileOut.delete();
			} catch (Exception e) {}
		}
		
		//Traitement de la file en Bout à bout
		if (Settings.btnSetBab.isSelected())
		{		
			final String extension =  fichier.substring(fichier.lastIndexOf("."));
			File listeBAB = new File(sortie.replace("\\", "/") + "/" + fichier.replace(extension, ".txt")); 			
			listeBAB.delete();
		}

		//Annulation
		if (cancelled)
		{
			try {
				fileOut.delete();
			} catch (Exception e) {}
			return true;
		}

		//Fichiers terminés
		if (cancelled == false && FFMPEG.error == false)
		{
			complete++;
			lblTermine.setText(Utils.fichiersTermines(complete));
		}
		
		//Ouverture du dossier
		if (caseOpenFolderAtEnd1.isSelected() && cancelled == false && FFMPEG.error == false)
		{
			try {
				Desktop.getDesktop().open(new File(sortie));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//Envoi par e-mail et FTP
		Utils.sendMail(fichier);
		Wetransfer.addFile(fileOut);
		Ftp.sendToFtp(fileOut);
		Utils.copyFile(fileOut);
		
		//Bout à bout
		if (Settings.btnSetBab.isSelected())
			return true;
		
		//MixAudio
		if (caseMixAudio.isSelected())
			return true;
		
		//Scan
		if (Shutter.scanIsRunning)
		{
			Utils.moveScannedFiles(fichier);			
			WAV.main();
			return true;
		}
		return false;
	}
	
}//Class