import React from 'react';
import { Card, Row, Col, Statistic, Tag, List, Typography, Spin } from 'antd';
import { useQuery } from '@apollo/client';
import { TrophyOutlined, TeamOutlined, CalendarOutlined, FireOutlined } from '@ant-design/icons';
import { DASHBOARD_METRICS_QUERY, DashboardMetrics } from '../../graphql';

const { Title, Text } = Typography;

const DashboardPage: React.FC = () => {
  const { data, loading } = useQuery<{ dashboardMetrics: DashboardMetrics }>(DASHBOARD_METRICS_QUERY);
  const m = data?.dashboardMetrics;

  if (loading) return <Spin size="large" style={{ display:'block', margin:'4rem auto' }} />;

  return (
    <div style={{ width: '100%' }}>
      <Title level={4} style={{ marginBottom: 16 }}>My Dashboard</Title>

      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={6}>
          <Card><Statistic title="Open Jobs" value={m?.openJobs ?? 0} prefix={<FireOutlined style={{ color:'#3f51b5' }} />} /></Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card><Statistic title="Applications" value={m?.activeCandidates ?? 0} prefix={<TeamOutlined style={{ color:'#4caf50' }} />} /></Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card><Statistic title="Screenings Pending" value={m?.pendingScreenings ?? 0} prefix={<CalendarOutlined style={{ color:'#ff9800' }} />} /></Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card><Statistic title="Avg Match Score" value={((m?.averageMatchScore ?? 0)*100).toFixed(1)} suffix="%" prefix={<TrophyOutlined style={{ color:'#e91e63' }} />} /></Card>
        </Col>
      </Row>

      <Row gutter={[12, 12]}>
        <Col xs={24} md={12}>
          <Card title="Top Skills in Demand">
            <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
              {(m?.topSkillsInDemand ?? []).map(s => (
                <Tag key={s.skill} color="blue">{s.skill} ({s.count})</Tag>
              ))}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="Recent Activity">
            <List
              dataSource={m?.recentActivity ?? []}
              renderItem={item => (
                <List.Item><Text>{item.description}</Text></List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default DashboardPage;
