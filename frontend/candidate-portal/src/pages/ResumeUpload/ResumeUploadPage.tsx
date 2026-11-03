import React, { useState } from 'react';
import { Upload, Button, Card, Alert, Progress, Typography, Divider, Tag } from 'antd';
import { InboxOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useDispatch, useSelector } from 'react-redux';
import { uploadResume } from '../../store/resumeSlice';
import { RootState, AppDispatch } from '../../store';

const { Dragger } = Upload;
const { Title, Text, Paragraph } = Typography;

const ResumeUploadPage: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const { uploading, error, parseStatus, resumeId } = useSelector((s: RootState) => s.resume);
  const [file, setFile] = useState<File | null>(null);

  const handleUpload = () => {
    if (file) dispatch(uploadResume(file));
  };

  return (
    <div style={{ maxWidth: 700, width: '100%' }}>
      <Title level={4}>Upload Your Resume</Title>
      <Paragraph type="secondary">
        Upload your resume (PDF, DOCX) and our AI will parse your skills, experience, and
        automatically match you to relevant job openings.
      </Paragraph>

      {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}

      {parseStatus === 'COMPLETED' && (
        <Alert icon={<CheckCircleOutlined />} type="success" showIcon
          message="Resume parsed successfully! Matching against open jobs..."
          style={{ marginBottom: 16 }} />
      )}

      <Card>
        <Dragger
          multiple={false} showUploadList={true} accept=".pdf,.doc,.docx"
          beforeUpload={f => { setFile(f); return false; }}
          style={{ padding: '20px' }}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">Click or drag your resume here to upload</p>
          <p className="ant-upload-hint">Supports PDF, DOC, DOCX — max 10MB</p>
        </Dragger>

        <Divider />

        <Button type="primary" size="large" block loading={uploading}
                disabled={!file} onClick={handleUpload}>
          {uploading ? 'Parsing Resume...' : 'Upload & Parse'}
        </Button>

        {uploading && <Progress percent={75} status="active" style={{ marginTop: 16 }} />}
      </Card>

      {resumeId && (
        <Card title="Parse Status" style={{ marginTop: 16 }}>
          <div>Resume ID: <Tag color="blue">{resumeId}</Tag></div>
          <div>Status: <Tag color={parseStatus === 'COMPLETED' ? 'green' : 'orange'}>{parseStatus}</Tag></div>
        </Card>
      )}
    </div>
  );
};

export default ResumeUploadPage;
