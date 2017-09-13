/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.akoya.codex.upload.driffta;

import com.akoya.codex.upload.logger;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.plugin.HyperStackConverter;
import ij.plugin.StackCombiner;
import ij.process.LUT;
import ij.process.StackProcessor;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 *
 * @author Nikolay Samusik
 */
public class MakeMontage {

    public static void main(String[] args) {

        //args = new String[]{"I:\\Nikolay\\4-20-16 Panel test on tonsil\\bestFocus", "2"};
        //args = new String[]{"I:\\Nikolay\\41-parameter 16 cycles melanoma Nikolay 4-18-17\\analysis\\montages\\New folder", "4"};
        if (args.length == 0) {
            System.err.println("USAGE:\n com.akoya.codex.upload.driffta.MakeMontage <path-to-best-focused-stacks> <optional:downsampling-factor (default:2)>");
            return;
        }

        int fc = 2;
        if (args.length == 2) {
            fc = Integer.parseInt(args[1]);
        }

        final int factor = fc;

        final File file = new File(args[0]);
        File[] tiff = file.listFiles(n -> n.getName().startsWith("reg") && n.getName().contains("_X") && n.getName().contains("_Y") && (n.getName().endsWith(".tiff") || n.getName().endsWith(".tif")));

        logger.print("Found " + tiff.length + " TIFF files:");
        for (File file1 : tiff) {
            logger.print(file1);
        }

        ImagePlus imp = new Opener().openImage(tiff[0].getAbsolutePath());

        Arrays.asList(tiff).stream().collect(Collectors.groupingBy(t -> t.getName().split("_")[0])).forEach((regname, filesInReg) -> {
            //final String regName = regname;
            int maxX = 0;
            int maxY = 0;
            for (File f2 : filesInReg) {
                try {
                    int[] coord = extractXYCoord(f2);
                    maxX = Math.max(maxX, coord[0]);
                    maxY = Math.max(maxY, coord[1]);
                } catch (Exception e) {

                }
            }
            ImageStack[][] grid = new ImageStack[maxX][maxY];

            for (File f2 : filesInReg) {
                try {
                    int[] coord = extractXYCoord(f2);

                    ImagePlus tmp = new Opener().openImage(f2.getAbsolutePath());

                    ImageStack is = tmp.getImageStack();

                    StackProcessor sp = new StackProcessor(is);

                    grid[coord[0] - 1][coord[1] - 1] = sp.resize(tmp.getWidth() / factor, tmp.getHeight() / factor);

                } catch (Exception e) {
                    logger.showException(e);
                }
            }

            for (int x = 0; x < grid.length; x++) {
                for (int y = 0; y < grid[x].length; y++) {
                    if (grid[x][y] == null) {
                        throw new IllegalStateException("tile is null: " + regname + " X=" + (x + 1) + ", Y=" + (y + 1));
                    }

                }
            }

            ImageStack[] horizStacks = new ImageStack[grid[0].length];

            StackCombiner stackCombiner = new StackCombiner();

            for (int y = 0; y < horizStacks.length; y++) {
                horizStacks[y] = grid[0][y];
                for (int x = 1; x < grid.length; x++) {
                    horizStacks[y] = stackCombiner.combineHorizontally(horizStacks[y], grid[x][y]);
                }
            }

            ImageStack out = horizStacks[0];

            for (int i = 1; i < horizStacks.length; i++) {
                if (horizStacks[i] != null) {
                    out = stackCombiner.combineVertically(out, horizStacks[i]);
                }
            }

            ImagePlus comb = new ImagePlus(regname, out);

            logger.print("combined stack has " + comb.getStackSize() + " slices");
            logger.print("imp stack has " + imp.getStackSize() + " slices, " + imp.getNChannels() + " channels, " + imp.getNFrames() + " frames, " + imp.getNSlices() + " slices");

            ImagePlus hyp = HyperStackConverter.toHyperStack(comb, imp.getNChannels(), 1, imp.getStackSize() / imp.getNChannels(), "xyczt", "composite");
            if (hyp.getNChannels() == 4) {
                ((CompositeImage) hyp).setLuts(new LUT[]{LUT.createLutFromColor(Color.WHITE), LUT.createLutFromColor(Color.RED), LUT.createLutFromColor(Color.GREEN), LUT.createLutFromColor(new Color(0, 70, 255))});
            }

            IJ.saveAsTiff(hyp, file.getAbsolutePath() + File.separator + regname + "_montage.tif");

        });

    }

    public static int[] extractXYCoord(File f) {
        String[] s = f.getName().split("[_\\.]");
        int[] ret = new int[]{Integer.parseInt(s[1].substring(1)), Integer.parseInt(s[2].substring(1))};
        return ret;
    }
}
