import React, { useEffect, useState } from 'react';
import { Card, Input, Row, Col, Tag, Button, Spin, Empty, Typography, message } from 'antd';
import { SearchOutlined, EnvironmentOutlined, DollarOutlined } from '@ant-design/icons';
import { useDispatch, useSelector } from 'react-redux';
import { useMutation } from '@apollo/client';
import { useNavigate } from 'react-router-dom';
import { fetchJobs } from '../../store/jobsSlice';
import { RootState, AppDispatch } from '../../store';
import { Job, START_SCREENING_MUTATION } from '../../graphql';

const { Title, Text } = Typography;

type StartScreeningResponse = {
  startScreening: {
    id: string;
    candidateId: string;
    jobId: string;
    currentStage: string;
    status: string;
    decision: string;
  };
};

const JobsPage: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const { items, loading, totalElements } = useSelector((s: RootState) => s.jobs);
  const user = useSelector((s: RootState) => s.auth.user);
  const [search, setSearch]   = useState('');
  const [applied, setApplied] = useState<Set<string>>(new Set());
  const [applying, setApplying] = useState<Set<string>>(new Set());
  const [sessionByJob, setSessionByJob] = useState<Record<string, string>>({});

  const [startScreening] = useMutation<StartScreeningResponse>(START_SCREENING_MUTATION);

  useEffect(() => { dispatch(fetchJobs({ page: 0, size: 20 })); }, [dispatch]);

  const handleSearch = () => dispatch(fetchJobs({ search, page: 0, size: 20 }));

  const handleApply = async (jobId: string) => {
    if (!user?.id) {
      message.error('Unable to identify candidate. Please sign in again.');
      return;
    }

    setApplying((prev: Set<string>) => new Set([...prev, jobId]));
    try {
      const { data } = await startScreening({
        variables: {
          candidateId: user.id,
          jobId,
        },
      });

      const sessionId = data?.startScreening?.id;
      if (!sessionId) {
        throw new Error('Screening session was not created.');
      }

      setApplied((prev: Set<string>) => new Set([...prev, jobId]));
      setSessionByJob((prev: Record<string, string>) => ({ ...prev, [jobId]: sessionId }));
      message.success('Application submitted. Continuing to interview prep.');
      navigate(`/interview?sessionId=${sessionId}`);
    } catch (error) {
      const reason = error instanceof Error ? error.message : 'Failed to submit application';
      message.error(reason);
    } finally {
      setApplying((prev: Set<string>) => {
        const next = new Set(prev);
        next.delete(jobId);
        return next;
      });
    }
  };

  return (
    <div>
      <Title level={4}>Find Jobs ({totalElements})</Title>
      <div style={{ display:'flex', gap:12, marginBottom:24, flexWrap:'wrap' }}>
        <Input placeholder="Search by title, skills..." value={search}
               onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearch(e.target.value)}
               onPressEnter={handleSearch} prefix={<SearchOutlined />} size="large"
               style={{ maxWidth:400, flex:'1 1 280px' }} />
        <Button size="large" onClick={handleSearch}>Search</Button>
      </div>

      {loading && <Spin size="large" style={{ display:'block', margin:'3rem auto' }} />}

      {!loading && items.length === 0 && <Empty description="No jobs found" />}

      <Row gutter={[16, 16]}>
        {items.map((job: Job) => (
          <Col xs={24} md={12} key={job.id}>
            <Card
              hoverable
              title={job.title}
              extra={<Tag color={job.status === 'OPEN' ? 'green' : 'default'}>{job.status}</Tag>}
              actions={[
                applied.has(job.id)
                  ? <Button onClick={() => navigate(`/interview?sessionId=${sessionByJob[job.id]}`)}>Continue Prep</Button>
                  : <Button loading={applying.has(job.id)} onClick={() => handleApply(job.id)}>Apply Now</Button>
              ]}
            >
              <div style={{ display:'flex', gap:8, marginBottom:8, color:'#666', fontSize:13 }}>
                <span><EnvironmentOutlined /> {job.location ?? 'Remote'}</span>
                <span>· {job.type}</span>
                {job.salaryMin && <span><DollarOutlined /> {(job.salaryMin/1000).toFixed(0)}k–{((job.salaryMax??0)/1000).toFixed(0)}k</span>}
              </div>
              <div style={{ display:'flex', flexWrap:'wrap', gap:4, marginBottom:8 }}>
                {job.skills.slice(0,5).map(s => <Tag key={s} color="blue">{s}</Tag>)}
                {job.skills.length > 5 && <Tag>+{job.skills.length-5}</Tag>}
              </div>
              {job.salaryConfidence && (
                <Text type="secondary" style={{ fontSize:12 }}>
                  AI salary confidence: {(job.salaryConfidence*100).toFixed(0)}%
                </Text>
              )}
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
};

export default JobsPage;
