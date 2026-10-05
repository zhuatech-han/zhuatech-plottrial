-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

CREATE TABLE field_site (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(60) NOT NULL,
 name varchar(120) NOT NULL,
 department_id bigint NOT NULL,
 created_by bigint NOT NULL,
 enabled boolean NOT NULL,
 version bigint NOT NULL
);

CREATE TABLE trial_plan (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(60) NOT NULL,
 name varchar(120) NOT NULL,
 site_id bigint NOT NULL,
 site_name varchar(120) NOT NULL,
 department_id bigint NOT NULL,
 created_by bigint NOT NULL,
 reviewer_id bigint NOT NULL,
 crop_type varchar(60) NOT NULL,
 crop_name varchar(120) NOT NULL,
 objective varchar(1000) NOT NULL,
 block_count int NOT NULL,
 plot_area decimal(16,4) NOT NULL,
 status varchar(30) NOT NULL,
 outcome varchar(30),
 start_date date,
 created_at timestamp(6) NOT NULL,
 approved_at timestamp(6),
 started_at timestamp(6),
 ended_at timestamp(6),
 closed_at timestamp(6),
 plan_hash varchar(64),
 layout_seed varchar(64),
 layout_hash varchar(64),
 data_hash varchar(64),
 version bigint NOT NULL
);

CREATE TABLE trial_editor (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 trial_id bigint NOT NULL,
 actor_id bigint NOT NULL
);

CREATE TABLE treatment (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 trial_id bigint NOT NULL,
 code varchar(60) NOT NULL,
 name varchar(120) NOT NULL,
 description varchar(500) NOT NULL,
 control boolean NOT NULL,
 enabled boolean NOT NULL,
 version bigint NOT NULL
);

CREATE TABLE measure_definition (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 trial_id bigint NOT NULL,
 code varchar(60) NOT NULL,
 name varchar(120) NOT NULL,
 unit varchar(30) NOT NULL,
 minimum decimal(16,4) NOT NULL,
 maximum decimal(16,4) NOT NULL,
 required boolean NOT NULL,
 enabled boolean NOT NULL,
 version bigint NOT NULL
);

CREATE TABLE trial_plot (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 trial_id bigint NOT NULL,
 block_index int NOT NULL,
 position int NOT NULL,
 label varchar(100) NOT NULL,
 treatment_id bigint NOT NULL,
 treatment_code varchar(60) NOT NULL,
 treatment_name varchar(120) NOT NULL,
 treatment_description varchar(500) NOT NULL,
 control boolean NOT NULL,
 observer_id bigint,
 received_at timestamp(6),
 planting_date date,
 excluded boolean NOT NULL,
 exclusion_status varchar(30) NOT NULL,
 exclusion_reason varchar(1000),
 exclusion_requested_by bigint,
 exclusion_reviewed_by bigint,
 version bigint NOT NULL
);

CREATE TABLE observation (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 plot_id bigint NOT NULL,
 measure_id bigint NOT NULL,
 revision int NOT NULL,
 `value` decimal(16,4),
 missing_reason varchar(1000) NOT NULL,
 note varchar(1000) NOT NULL,
 observed_date date NOT NULL,
 status varchar(30) NOT NULL,
 created_by bigint NOT NULL,
 recorded_at timestamp(6) NOT NULL,
 reviewed_by bigint,
 accepted_at timestamp(6),
 supersedes_id bigint,
 version bigint NOT NULL
);

ALTER TABLE field_site ADD FOREIGN KEY(department_id) REFERENCES department(id);
ALTER TABLE field_site ADD FOREIGN KEY(created_by) REFERENCES account(id);
ALTER TABLE trial_plan ADD FOREIGN KEY(site_id) REFERENCES field_site(id);
ALTER TABLE trial_plan ADD FOREIGN KEY(department_id) REFERENCES department(id);
ALTER TABLE trial_plan ADD FOREIGN KEY(created_by) REFERENCES account(id);
ALTER TABLE trial_plan ADD FOREIGN KEY(reviewer_id) REFERENCES account(id);
ALTER TABLE trial_editor ADD FOREIGN KEY(trial_id) REFERENCES trial_plan(id);
ALTER TABLE trial_editor ADD FOREIGN KEY(actor_id) REFERENCES account(id);
ALTER TABLE treatment ADD FOREIGN KEY(trial_id) REFERENCES trial_plan(id);
ALTER TABLE measure_definition ADD FOREIGN KEY(trial_id) REFERENCES trial_plan(id);
ALTER TABLE trial_plot ADD FOREIGN KEY(trial_id) REFERENCES trial_plan(id);
ALTER TABLE trial_plot ADD FOREIGN KEY(treatment_id) REFERENCES treatment(id);
ALTER TABLE trial_plot ADD FOREIGN KEY(observer_id) REFERENCES account(id);
ALTER TABLE trial_plot ADD FOREIGN KEY(exclusion_requested_by) REFERENCES account(id);
ALTER TABLE trial_plot ADD FOREIGN KEY(exclusion_reviewed_by) REFERENCES account(id);
ALTER TABLE observation ADD FOREIGN KEY(plot_id) REFERENCES trial_plot(id);
ALTER TABLE observation ADD FOREIGN KEY(measure_id) REFERENCES measure_definition(id);
ALTER TABLE observation ADD FOREIGN KEY(created_by) REFERENCES account(id);
ALTER TABLE observation ADD FOREIGN KEY(reviewed_by) REFERENCES account(id);
ALTER TABLE observation ADD FOREIGN KEY(supersedes_id) REFERENCES observation(id);
ALTER TABLE field_site ADD UNIQUE(reference);
ALTER TABLE trial_plan ADD UNIQUE(reference);
ALTER TABLE trial_editor ADD UNIQUE(trial_id,actor_id);
ALTER TABLE treatment ADD UNIQUE(trial_id,code);
ALTER TABLE measure_definition ADD UNIQUE(trial_id,code);
ALTER TABLE trial_plot ADD UNIQUE(trial_id,block_index,position);
ALTER TABLE trial_plot ADD UNIQUE(label);
ALTER TABLE observation ADD UNIQUE(plot_id,measure_id,revision);
ALTER TABLE field_site ADD CHECK(version>=1);
ALTER TABLE trial_plan ADD CHECK(version>=1);
ALTER TABLE treatment ADD CHECK(version>=1);
ALTER TABLE measure_definition ADD CHECK(version>=1);
ALTER TABLE trial_plot ADD CHECK(version>=1);
ALTER TABLE observation ADD CHECK(version>=1);
ALTER TABLE trial_plan ADD CHECK(block_count BETWEEN 4 AND 16 AND plot_area>0);
ALTER TABLE measure_definition ADD CHECK(minimum<=maximum);
ALTER TABLE observation ADD CHECK(revision>=1);
CREATE INDEX ix_plot_observer ON trial_plot(observer_id,trial_id);
CREATE INDEX ix_trial_scope ON trial_plan(department_id,created_by);
CREATE TABLE command_record (id bigint AUTO_INCREMENT PRIMARY KEY,request_key varchar(36) NOT NULL UNIQUE,fingerprint varchar(64) NOT NULL,response_json longtext NOT NULL);
CREATE TABLE business_event (id bigint AUTO_INCREMENT PRIMARY KEY,object_type varchar(30) NOT NULL,object_id bigint NOT NULL,actor_id bigint NOT NULL,action varchar(60) NOT NULL,note varchar(1000) NOT NULL,snapshot longtext NOT NULL,created_at timestamp(6) NOT NULL,FOREIGN KEY(actor_id) REFERENCES account(id));
CREATE INDEX ix_event_object ON business_event(object_type,object_id,id);
