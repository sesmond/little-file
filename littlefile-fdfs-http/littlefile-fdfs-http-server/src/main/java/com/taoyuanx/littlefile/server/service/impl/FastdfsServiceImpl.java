package com.taoyuanx.littlefile.server.service.impl;

import com.taoyuanx.littlefile.fdfshttp.core.dto.FileInfo;
import com.taoyuanx.littlefile.fdfshttp.core.dto.MasterAndSlave;
import com.taoyuanx.littlefile.server.config.FileProperties;
import com.taoyuanx.littlefile.server.dto.ImageWH;
import com.taoyuanx.littlefile.server.ex.ServiceException;
import com.taoyuanx.littlefile.server.service.FastdfsService;
import com.taoyuanx.littlefile.server.service.FileValidateService;
import com.taoyuanx.littlefile.server.utils.CodeUtil;
import com.taoyuanx.littlefile.server.utils.FdfsFileUtil;
import com.taoyuanx.littlefile.server.utils.FilenameUtils;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class FastdfsServiceImpl implements FastdfsService {
    @Autowired
    private FileValidateService fileValidateService;
    @Autowired
    FdfsFileUtil fdfsFileUtil;

    @Autowired
    FileProperties fileProperties;


    @Override
    public String uploadFile(MultipartFile file) throws ServiceException {
        try {
            fileValidateService.validateFile(file);
            long fileSize = file.getSize();
            if (fileSize <= 0) {
                throw new ServiceException("file is null.");
            }
            String path = fdfsFileUtil.upload(file.getInputStream(), file.getOriginalFilename());
            if (path == null) {
                throw new ServiceException("upload error.");
            }
            return path;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传异常-->", e);
            throw new ServiceException("文件上传异常");
        }
    }

    @Override
    public String uploadSlaveFile(String masterFileId, MultipartFile file) throws ServiceException {
        try {
            fileValidateService.validateFile(file);
            long fileSize = file.getSize();
            if (fileSize <= 0) {
                throw new ServiceException("file is null.");
            }
            String fileName = file.getOriginalFilename();
            //随机生成从文件前缀,防止重名异常
            String filePrefixName = FilenameUtils.getPrefixRandom(fileName);
            String path = fdfsFileUtil.uploadSlave(masterFileId, file.getBytes(), filePrefixName, fileName);
            if (path == null) {
                throw new ServiceException("slave upload error.");
            }
            return path;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件从上传异常-->", e);
            throw new ServiceException("文件上传异常");
        }
    }

    @Override
    public String uploadSlaveFile(String masterFilename, String prefixName, MultipartFile file) throws ServiceException {
        try {
            fileValidateService.validateFile(file);
            long fileSize = file.getSize();
            if (fileSize <= 0) {
                throw new ServiceException("file is null.");
            }
            prefixName = prefixName + "_" + FilenameUtils.generateShortUuid();
            String path = fdfsFileUtil.uploadSlave(masterFilename, file.getBytes(), prefixName, file.getOriginalFilename());
            if (path == null) {
                throw new ServiceException("slave upload error.");
            }
            return path;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件从上传异常-->", e);
            throw new ServiceException("文件上传异常");
        }
    }


    @Override
    public MasterAndSlave uploadImageAndThumb(String cutSize, MultipartFile file) throws ServiceException {
        try {
            fileValidateService.validateFile(file);
            long fileSize = file.getSize();
            if (fileSize <= 0) {
                throw new ServiceException("file is null.");
            }
            if (StringUtils.isEmpty(cutSize)) {
                throw new ServiceException("cutSize is null.");
            }
            String ext = FilenameUtils.getExtension(file.getOriginalFilename());
            //临时落盘,防止文件过大
            File sourceFile = new File(fileProperties.getFileCacheDir(), CodeUtil.getUUID());
            file.transferTo(sourceFile);
            //生成缩略图
            List<ImageWH> whs = loadCutSize(cutSize);
            int len = whs.size();
            InputStream[] slaveInputs = new InputStream[len];
            List<String> slaveNames = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageWH wh = whs.get(i);
                Thumbnails.of(sourceFile).size(wh.getW(), wh.getH()).toOutputStream(out);
                slaveInputs[i] = new ByteArrayInputStream(out.toByteArray());
                slaveNames.add(String.format("%dx%d", wh.getW(), wh.getH()) + "." + ext);
            }
            MasterAndSlave uploadMasterAndSlave = fdfsFileUtil.uploadMasterAndSlave(null, new FileInputStream(sourceFile),
                    file.getOriginalFilename(),
                    slaveNames, slaveInputs);
            //删除临时
            FileUtils.deleteQuietly(sourceFile);
            return uploadMasterAndSlave;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传图片异常-->", e);
            throw new ServiceException("文件上传异常");
        }
    }

    @Override
    public boolean removeFile(String fileId) throws ServiceException {
        try {
            if (StringUtils.isEmpty(fileId)) {
                throw new ServiceException("fileId is null");
            }
            fdfsFileUtil.delete(fileId);
            return true;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除异常-->", e);
            throw new ServiceException("删除异常");
        }
    }

    @Override
    public void download(String fileId, OutputStream outputStream) throws ServiceException {
        try {
            if (StringUtils.isEmpty(fileId)) {
                throw new ServiceException("fileId is null");
            }
            fdfsFileUtil.download(fileId, outputStream);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载文件异常-->", e);
            throw new ServiceException("下载文件异常");
        }
    }

    @Override
    public void download(String fileId, Long start, Long len, OutputStream outputStream) throws ServiceException {
        try {
            if (StringUtils.isEmpty(fileId)) {
                throw new ServiceException("fileId is null");
            }
            fdfsFileUtil.download(fileId, start, len, outputStream);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载文件异常-->", e);
            throw new ServiceException("下载文件异常");
        }
    }


    private List<ImageWH> loadCutSize(String cutSize) throws ServiceException {
        List<ImageWH> whs = null;
        if (!StringUtils.isEmpty(cutSize)) {
            try {
                List<String> sizes = Arrays.asList(cutSize.split(","));
                whs = new ArrayList<>();
                for (String size : sizes) {
                    String vals[] = size.split("x");
                    int w = Integer.parseInt(vals[0]);
                    int h = Integer.parseInt(vals[1]);
                    whs.add(new ImageWH(w, h));
                }

            } catch (Exception e) {
                throw new ServiceException("cutSize is error");
            }
        }
        return whs;
    }

    @Override
    public FileInfo getFileInfo(String fileId) throws ServiceException {

        try {
            if (StringUtils.isEmpty(fileId)) {
                throw new ServiceException("fileId is null");
            }
            org.csource.fastdfs.FileInfo fileInfo = fdfsFileUtil.getFileInfo(fileId);
            if (null == fileInfo) {
                throw new ServiceException("文件不存在");
            }
            FileInfo info = new FileInfo();
            info.setCrc32(fileInfo.getCrc32());
            info.setCreate_timestamp(fileInfo.getCreateTimestamp().getTime());
            info.setFile_size(fileInfo.getFileSize());
            return info;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件信息-->", e);
            throw new ServiceException("文件信息获取异常");
        }
    }


}
